data class TimerStats(
    val userId: String = "",
    var totalFocusMinutes: Int = 0,
    var completedSessions: Int = 0,
    var currentStreak: Int = 0,
    var maxStreak: Int = 0,
    var perfectSessions: Int = 0,
    var sessionsWith1Ticket: Int = 0,
    var sessionsWith2Tickets: Int = 0,
    var sessionsWith3PlusTickets: Int = 0
) {
    constructor() : this("")
}