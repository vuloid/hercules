package app.hercules.store.data

object GithubUrl {
    private val pattern = Regex("""^https://github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?/?$""")

    fun parse(input: String): Pair<String, String>? {
        val match = pattern.matchEntire(input.trim()) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }
}
