package app.warden

import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.warden.api.IAuditListener
import app.warden.api.IRemoteProcess
import app.warden.api.IWarden
import app.warden.api.WardenSu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Live checks against a running broker on a real device. Start the broker from
 * the app first; the tests skip (not fail) when it isn't running.
 *
 * Runs in the manager's process (an ordinary app SELinux domain), so calls are
 * made as the manager uid; grant denial for other apps isn't covered here.
 */
@RunWith(AndroidJUnit4::class)
class BrokerLiveTest {
    private lateinit var warden: IWarden

    @Before fun bind() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // A freshly (re)started app has no binder yet; the provider says hello to
        // the broker and waits for its handshake before answering.
        val binder: IBinder? = ctx.contentResolver.call(Uri.parse("content://app.warden.broker"), "getBinder", null, null)
            ?.getBinder("binder")
        assumeTrue("broker not running — tap Start first", binder?.isBinderAlive == true)
        warden = IWarden.Stub.asInterface(binder)
    }

    private fun run(vararg cmd: String): Pair<Int, String> {
        val p: IRemoteProcess = warden.newProcess(arrayOf(*cmd), emptyArray(), "/")
        val text = ParcelFileDescriptor.AutoCloseInputStream(p.inputStream).bufferedReader().readText()
        return p.waitFor() to text
    }

    @Test fun reportsShellIdentity() {
        assertEquals(1, warden.apiVersion())
        assertEquals(2000, warden.serverUid())
    }

    @Test fun execRunsAsShell() {
        val (code, out) = run("sh", "-c", "id -u")
        assertEquals(0, code)
        assertEquals("2000", out.trim())
    }

    @Test fun execCanDoWhatAnAppCannot() {
        // dumpsys needs android.permission.DUMP, which apps can't hold; shell can.
        val (code, out) = run("sh", "-c", "dumpsys battery")
        assertEquals(0, code)
        assertTrue("no battery level in: $out", out.contains("level:"))
    }

    @Test fun execPropagatesExitCode() {
        assertEquals(7, run("sh", "-c", "exit 7").first)
    }

    @Test fun transactAsForwardsToSystemService() {
        val sm = Class.forName("android.os.ServiceManager")
        val pkg = runCatching { sm.getMethod("getService", String::class.java).invoke(null, "package") as IBinder? }
            .getOrNull()
        assumeTrue("ServiceManager.getService blocked by hidden-API policy", pkg != null)
        val wrapped = warden.transactAs(pkg!!)
        assertEquals("android.content.pm.IPackageManager", wrapped.interfaceDescriptor)
    }

    @Test fun callsAreAudited() {
        val marker = "warden-audit-probe-${System.nanoTime()}"
        run("sh", "-c", "echo $marker")
        val log = ParcelFileDescriptor.AutoCloseInputStream(warden.auditTail()).bufferedReader().readText()
        assertTrue("audit missing $marker", log.contains(marker))
    }

    @Test fun auditLinesArePushed() {
        val marker = "warden-audit-push-${System.nanoTime()}"
        val seen = CountDownLatch(1)
        val listener = object : IAuditListener.Stub() {
            override fun onLine(line: String) { if (line.contains(marker)) seen.countDown() }
            override fun onCleared() = Unit
        }
        warden.watchAudit(listener)
        try {
            run("sh", "-c", "echo $marker")
            assertTrue("audit line not pushed", seen.await(5, TimeUnit.SECONDS))
        } finally {
            warden.unwatchAudit(listener)
        }
    }

    @Test fun grantsAreReadable() {
        assertNotNull(warden.grantsJson())
    }

    /** Runs the real shim binary from this app's process, as a client app would. */
    private fun su(cmd: String): Pair<Int, String> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("shim not extracted � run with -Pwarden.suTest",
            File(ctx.applicationInfo.nativeLibraryDir, "libwardensu.so").exists())
        val bin = WardenSu.install(ctx)
        val p = ProcessBuilder(File(bin, "su").path, "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return p.waitFor() to out
    }

    @Test fun suShimRunsAsShellFromAnApp() {
        val (code, out) = su("id -u")
        assertEquals(out, 0, code)
        assertEquals("2000", out.trim())
    }

    @Test fun suShimRelaysStderrAndExitCode() {
        val (code, out) = su("echo oops >&2; exit 3")
        assertEquals(3, code)
        assertTrue(out, out.contains("oops"))
    }

    @Test fun suShimRunsAWholeMultiLineScript() {
        // Each line used to be dropped after the first.
        val (code, out) = su("echo one\necho two\nexit 4")
        assertEquals(4, code)
        assertEquals("one\ntwo", out.trim())
    }

    @Test fun suShimPassesNulBytesThrough() {
        // A NUL in the output was taken as the exit-code trailer (screencap's PNG came out 8 bytes).
        val (code, out) = su("printf 'a\\000b\\000c'; exit 0")
        assertEquals(0, code)
        assertEquals("a\u0000b\u0000c", out)
    }
}
