package com.sandolpin.weatherquake.data.weather

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * 総務省「全国地方公共団体コード」を元にした、市区町村名(漢字)→読み(ひらがな)の辞書
 * (assets/japan_cities_kana.json、全1,747市区町村)。
 *
 * Open-Meteoのジオコーディング検索(WeatherRepository.searchLocations)は、language=jaを
 * 指定しても検索キーワード自体は主にローマ字/英語表記の索引に対してマッチングされているらしく、
 * 「前橋」のような漢字入力ではヒットしないが「maebashi」ならヒットする、という実機での
 * 検証結果があった。
 * そこで、漢字入力での検索が0件だった場合に限り、この辞書で読みを引いてローマ字に変換し、
 * そのローマ字でOpen-Meteoを再検索することで、漢字入力でも正しく地点が見つかるようにする。
 */
private data class CityKanaEntry(
    @SerializedName("n") val name: String,   // 市区町村名(都道府県名を含まない。例:「前橋市」)
    @SerializedName("k") val kana: String,   // 読み(ひらがな。例:「まえばしし」)
    @SerializedName("p") val pref: String    // 都道府県名(例:「群馬県」)
)

object JapaneseCityDictionary {

    private const val ASSET_FILE_NAME = "japan_cities_kana.json"

    @Volatile
    private var cachedEntries: List<CityKanaEntry>? = null

    private fun loadEntries(context: Context): List<CityKanaEntry> {
        cachedEntries?.let { return it }
        synchronized(this) {
            cachedEntries?.let { return it }
            val result = runCatching {
                context.assets.open(ASSET_FILE_NAME).use { input ->
                    InputStreamReader(input, "UTF-8").use { reader ->
                        val type = object : TypeToken<List<CityKanaEntry>>() {}.type
                        Gson().fromJson<List<CityKanaEntry>>(reader, type)
                    }
                }
            }.getOrDefault(emptyList())
            cachedEntries = result
            return result
        }
    }

    /**
     * 入力された市区町村名(漢字。例:「前橋」「前橋市」)に一致・前方一致・部分一致する
     * 市区町村を辞書から探し、そのローマ字表記(簡易ヘボン式変換)を返す。見つからなければnull。
     *
     * 完全一致 → 前方一致 → 部分一致 の順に緩めて検索することで、
     * 「前橋」だけの入力でも「前橋市」にマッチできるようにしている。
     */
    fun lookupRomajiReading(context: Context, rawQuery: String): String? {
        val entries = loadEntries(context)
        if (entries.isEmpty()) return null

        val query = rawQuery.trim()
        if (query.isEmpty()) return null

        val matched = entries.firstOrNull { it.name == query }
            ?: entries.firstOrNull { it.name.startsWith(query) }
            ?: entries.firstOrNull { it.name.contains(query) || "${it.pref}${it.name}".contains(query) }
            ?: return null

        return simplifyLongVowels(hiraganaToRomaji(matched.kana))
    }

    /** ひらがな/カタカナ/漢字のいずれかを含むかどうか(日本語入力かどうかの簡易判定) */
    fun containsJapaneseScript(text: String): Boolean = text.any { ch ->
        val code = ch.code
        (code in 0x3040..0x309F) || // ひらがな
                (code in 0x30A0..0x30FF) || // カタカナ
                (code in 0x4E00..0x9FFF)    // 漢字(CJK統合漢字)
    }

    // ============================== ひらがな→ローマ字(簡易ヘボン式) ==============================

    private val HIRAGANA_ROMAJI: Map<String, String> = mapOf(
        // 拗音(2文字)は1文字の五十音より先に判定する必要があるため、後述のchunkAt側で優先している
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "o", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po"
    )

    private val VOWELS = setOf('a', 'i', 'u', 'e', 'o')

    private fun hiraganaToRomaji(kana: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < kana.length) {
            val ch = kana[i]

            // 促音「っ」: 次の音の子音を重ねる(例: がっこう→gakkou)
            if (ch == 'っ') {
                val nextChunk = chunkAt(kana, i + 1)
                val firstConsonant = nextChunk?.first?.firstOrNull()
                if (firstConsonant != null && firstConsonant !in VOWELS) {
                    sb.append(firstConsonant)
                }
                i += 1
                continue
            }

            val chunk = chunkAt(kana, i)
            if (chunk != null) {
                sb.append(chunk.first)
                i += chunk.second
            } else {
                // 辞書に無い文字(記号など)はそのまま通す
                sb.append(ch)
                i += 1
            }
        }
        return sb.toString()
    }

    /** kanaのindex位置から、拗音(2文字)を優先して1音ぶんのromaji変換を試みる */
    private fun chunkAt(kana: String, index: Int): Pair<String, Int>? {
        if (index >= kana.length) return null
        if (index + 1 < kana.length) {
            HIRAGANA_ROMAJI[kana.substring(index, index + 2)]?.let { return it to 2 }
        }
        HIRAGANA_ROMAJI[kana[index].toString()]?.let { return it to 1 }
        return null
    }

    /** 「おう」→「お」、「おお」→「お」等、長音による母音の重複を1文字に簡略化する
     *  (一般的な地名ローマ字表記の慣習に合わせ、Open-Meteo側の索引とマッチしやすくするため) */
    private fun simplifyLongVowels(romaji: String): String {
        return romaji
            .replace("ou", "o")
            .replace("oo", "o")
            .replace("uu", "u")
            .replace("aa", "a")
            .replace("ii", "i")
            .replace("ee", "e")
    }
}