package com.moodledger.core

enum class TxnType { EXPENSE, INCOME }

enum class Category(val label: String, val emoji: String, val type: TxnType, keywords: List<String>) {
    FOOD(
        "Food & Drinks", "🍜", TxnType.EXPENSE,
        listOf(
            "food", "dinner", "lunch", "breakfast", "brunch", "supper", "meal", "snack", "snacks",
            "coffee", "kopi", "teh", "tea", "bubble tea", "boba", "drink", "drinks", "beer", "wine",
            "restaurant", "cafe", "hawker", "mamak", "nasi", "roti", "mee", "laksa", "chicken rice",
            "pizza", "burger", "sushi", "ramen", "bbq", "steamboat", "hotpot", "dessert", "cake",
            "starbucks", "mcdonald", "mcd", "kfc", "subway", "foodpanda", "grabfood", "deliveroo",
            "tealive", "chagee", "luckin", "koi", "toast box", "ya kun", "din tai fung",
        ),
    ),
    GROCERIES(
        "Groceries", "🛒", TxnType.EXPENSE,
        listOf(
            "grocery", "groceries", "supermarket", "market", "wet market", "fruit", "fruits",
            "vegetables", "ntuc", "fairprice", "giant", "cold storage", "sheng siong", "aeon",
            "lotus", "lotus's", "tesco", "jaya grocer", "mydin", "99 speedmart", "7-eleven", "7eleven",
        ),
    ),
    TRANSPORT(
        "Transport", "🚕", TxnType.EXPENSE,
        listOf(
            "grab", "gojek", "taxi", "cab", "uber", "bus", "mrt", "lrt", "train", "ktm", "ets",
            "petrol", "fuel", "gas", "parking", "toll", "erp", "ez-link", "ezlink", "touch n go",
            "tng", "car wash", "commute", "transport", "ferry",
        ),
    ),
    SHOPPING(
        "Shopping", "🛍️", TxnType.EXPENSE,
        listOf(
            "shopping", "clothes", "shirt", "shoes", "dress", "bag", "shopee", "lazada", "amazon",
            "taobao", "uniqlo", "zara", "ikea", "gadget", "phone", "laptop", "headphones", "daiso",
        ),
    ),
    BILLS(
        "Bills & Utilities", "🧾", TxnType.EXPENSE,
        listOf(
            "bill", "bills", "electric", "electricity", "water", "utilities", "internet", "wifi",
            "broadband", "mobile plan", "postpaid", "phone bill", "tnb", "sp services", "singtel",
            "starhub", "m1", "maxis", "celcom", "digi", "unifi", "insurance", "tax", "conservancy",
        ),
    ),
    HOUSING(
        "Housing", "🏠", TxnType.EXPENSE,
        listOf("rent", "mortgage", "housing", "condo", "maintenance fee", "renovation", "furniture"),
    ),
    SUBSCRIPTIONS(
        "Subscriptions", "🔁", TxnType.EXPENSE,
        listOf(
            "subscription", "netflix", "spotify", "youtube premium", "disney", "icloud",
            "google one", "chatgpt", "claude", "apple music", "prime", "gym membership",
        ),
    ),
    ENTERTAINMENT(
        "Entertainment", "🎬", TxnType.EXPENSE,
        listOf(
            "movie", "movies", "cinema", "gsc", "tgv", "concert", "game", "games", "steam",
            "karaoke", "ktv", "bowling", "museum", "theme park", "tickets", "netflix party",
        ),
    ),
    HEALTH(
        "Health & Fitness", "💊", TxnType.EXPENSE,
        listOf(
            "doctor", "clinic", "hospital", "dentist", "medicine", "pharmacy", "guardian",
            "watsons", "gym", "yoga", "massage", "physio", "spa", "haircut", "barber", "salon",
        ),
    ),
    TRAVEL(
        "Travel", "✈️", TxnType.EXPENSE,
        listOf("flight", "hotel", "airbnb", "hostel", "travel", "trip", "visa", "airasia", "scoot", "agoda", "booking.com"),
    ),
    EDUCATION(
        "Education", "📚", TxnType.EXPENSE,
        listOf("book", "books", "course", "class", "tuition", "school", "udemy", "coursera", "exam"),
    ),
    GIFTS(
        "Gifts & Family", "🎁", TxnType.EXPENSE,
        listOf("gift", "gifts", "present", "angpao", "ang pao", "red packet", "donation", "charity", "wedding", "birthday"),
    ),
    SALARY(
        "Salary", "💼", TxnType.INCOME,
        listOf("salary", "paycheck", "payday", "wage", "wages", "bonus", "commission"),
    ),
    OTHER_INCOME(
        "Other Income", "💰", TxnType.INCOME,
        listOf("income", "refund", "cashback", "dividend", "interest", "received", "reimbursement", "sold", "freelance"),
    ),
    OTHER("Other", "📦", TxnType.EXPENSE, emptyList());

    /** Keywords sorted longest first so "chicken rice" beats "rice". */
    val keywords: List<String> = keywords.sortedByDescending { it.length }

    companion object {
        fun expenseCategories() = entries.filter { it.type == TxnType.EXPENSE }
        fun incomeCategories() = entries.filter { it.type == TxnType.INCOME }
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Keyword based classifier. It is intentionally simple and deterministic so it works offline;
 * user corrections are fed back through [learned] (merchant/phrase -> category) which wins
 * over the built-in keyword table.
 */
class CategoryClassifier(private val learned: Map<String, Category> = emptyMap()) {

    data class Result(val category: Category, val matchedKeyword: String?)

    fun classify(text: String): Result {
        val normalized = " " + text.lowercase().replace(Regex("[^\\p{L}\\p{N}'&.\\- ]"), " ")
            .replace(Regex("\\s+"), " ").trim() + " "

        learned.entries.sortedByDescending { it.key.length }.forEach { (phrase, cat) ->
            if (containsWord(normalized, phrase.lowercase())) return Result(cat, phrase)
        }
        // Earliest match in the text wins ties, and longer keywords are checked first per category.
        var best: Pair<Int, Result>? = null
        for (cat in Category.entries) {
            for (kw in cat.keywords) {
                val idx = indexOfWord(normalized, kw)
                if (idx >= 0) {
                    val score = idx * 100 - kw.length
                    if (best == null || score < best.first) best = score to Result(cat, kw)
                    break
                }
            }
        }
        return best?.second ?: Result(Category.OTHER, null)
    }

    private fun containsWord(haystack: String, word: String) = indexOfWord(haystack, word) >= 0

    private fun indexOfWord(haystack: String, word: String): Int {
        val m = Regex("(?<=[\\s])" + Regex.escape(word) + "(?=[\\s.'s])").find(haystack)
        return m?.range?.first ?: -1
    }
}
