package app.warden.api;

/** Receives audit lines as the server writes them (manager UI's live feed). */
oneway interface IAuditListener {
    /** One JSONL line, exactly as appended to the log. */
    void onLine(String line);
    /** The log was wiped; drop what's shown. */
    void onCleared();
}
