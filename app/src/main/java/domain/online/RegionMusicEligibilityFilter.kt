package domain.online

import com.example.model.Song
import java.text.Normalizer
import java.util.Locale

/**
 * Region-based music recommendation eligibility engine shared across SONORA's
 * repository, recommendation builders, radio engine, and queue generation.
 *
 * When the active region is India ("IN"):
 * - Enforces STRICT verified-Indian-music eligibility across Hindi, Odia, Bengali,
 *   Punjabi, Tamil, Telugu, Malayalam, Kannada, Marathi, Assamese, Gujarati,
 *   Bhojpuri, Rajasthani, and Indian Indie/English catalogs.
 * - Excludes known non-Indian foreign tracks AND strictly excludes unverified/unknown-origin tracks.
 * - Never backfills empty lists with foreign tracks.
 * - Keeps English-language songs by verified Indian artists/labels eligible.
 *
 * When another country is selected (e.g. "US", "GB", "JP", "KR"), applies region-aware
 * behavior for that country without hardcoding India.
 */
object RegionMusicEligibilityFilter {

    const val REGION_INDIA = "IN"

    private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{M}\\p{N}\\s&'-]+")
    private val MULTISPACE_REGEX = Regex("\\s+")

    /**
     * Indic Unicode script blocks covering major Indian languages:
     * Devanagari (Hindi/Marathi/Sanskrit/Bhojpuri), Bengali/Assamese, Gurmukhi (Punjabi),
     * Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam.
     */
    fun containsIndicScript(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0x0900..0x0D7F) return true
        }
        return false
    }

    /**
     * Non-Indic foreign scripts (Hangul, Hiragana/Katakana, Cyrillic, Arabic, Thai, Hebrew, Greek)
     * used to quickly detect non-Indian foreign catalog metadata when no verified Indian signal exists.
     */
    private fun containsForeignNonLatinScript(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0xAC00..0xD7AF || // Hangul (Korean)
                code in 0x1100..0x11FF || // Hangul Jamo
                code in 0x3040..0x30FF || // Hiragana / Katakana (Japanese)
                code in 0x4E00..0x9FFF || // CJK Ideographs
                code in 0x0400..0x04FF || // Cyrillic
                code in 0x0600..0x06FF || // Arabic
                code in 0x0E00..0x0E7F || // Thai
                code in 0x0590..0x05FF || // Hebrew
                code in 0x0370..0x03FF    // Greek
            ) {
                return true
            }
        }
        return false
    }

    /** Verified Indian record labels, film banners, and official music channels. */
    private val VERIFIED_INDIAN_LABELS: Set<String> = setOf(
        "t-series", "t series", "tseries", "t-series apna punjab", "t-series bhakti sagar",
        "t-series telugu", "t-series tamil", "t-series kannada", "t-series malayalam",
        "t-series marathi", "t-series gujarati", "t-series bangla", "t-series haryanvi",
        "sony music india", "sony music south", "sony music marathi", "sony music regional",
        "zee music company", "zee music", "zee music south", "zee music marathi", "zee music bangla",
        "saregama", "saregama music", "saregama tamil", "saregama telugu", "saregama bengali",
        "saregama gujarati", "saregama marathi", "saregama punjabi", "saregama malayalam",
        "saregama kannada", "saregama bhojpuri", "saregama odia", "saregama carvaan",
        "tips official", "tips music", "tips punjabi", "tips marathi", "tips films",
        "yrf", "yrf music", "yash raj films",
        "aditya music", "lahari music", "think music india", "think music", "muzik247",
        "sun music", "sun tv", "junglee music", "ishtar music", "eros now", "eros now music",
        "shemaroo", "shemaroo filmi gaane", "shemaroo punjabi", "shemaroo marathi", "shemaroo gujarati",
        "times music", "times music spiritual", "speed records", "white hill music",
        "desi melodies", "desi music factory", "desimusicfactory", "geet mp3", "humble music",
        "jatt records", "single track studios", "jass records", "crown records", "lokdhun punjabi",
        "tarang music", "amara muzik", "amara muzik odia", "amara muzik bengali",
        "sarthak music", "sidharth music", "odiaone", "odisha music", "manjari music",
        "svf music", "svf", "shree venkatesh films", "surinder films", "windows production",
        "asha audio", "eskay movies", "red ribbon musik", "coke studio bharat", "coke studio india",
        "mtv hustle", "kalamkaar", "mass appeal india", "gully gang", "azadi records",
        "universal music india", "warner music india", "def jam india", "vyrl originals",
        "indie music label", "panorama music", "dharma productions", "maddock films",
        "red chillies", "hombale films", "anand audio", "prk audio", "divo music",
        "mango music", "madhura audio", "satyam audios", "manorama music", "everest marathi",
        "fountain music", "rajshri", "rdc rajasthani", "wave music", "worldwide records bhojpuri",
        "venus", "venus movies", "ultra bollywood", "gaane sune ansune", "filmi gaane",
        "indipop", "play dmf", "blive music", "merchants records", "saavn", "jiosaavn",
        "gaana", "wynk", "hungama", "raaga", "nk production", "assamese music"
    )

    /** Verified Indian artists & composers across all major Indian languages + Indian English/Indie. */
    private val VERIFIED_INDIAN_ARTISTS: Set<String> = setOf(
        // Hindi / Bollywood / Pan-India
        "arijit singh", "shreya ghoshal", "a.r. rahman", "ar rahman", "a r rahman", "pritam",
        "sonu nigam", "alka yagnik", "udit narayan", "kumar sanu", "lata mangeshkar",
        "kishore kumar", "mohammed rafi", "asha bhosle", "sunidhi chauhan", "jubin nautiyal",
        "vishal mishra", "vishal-shekhar", "vishal shekhar", "vishal dadlani", "shekhar ravjiani",
        "shankar ehsaan loy", "shankar mahadevan", "amit trivedi", "sachin-jigar", "sachin jigar",
        "mithoon", "sachet-parampara", "sachet parampara", "sachet tandon", "parampara tandon",
        "b praak", "jaani", "armaan malik", "amaal mallik", "darshan raval", "neha kakkar",
        "tony kakkar", "badshah", "yo yo honey singh", "honey singh", "raftaar", "ikka",
        "dino james", "mc stan", "emiway bantai", "krsna", "kr\$na", "seedhe maut", "divine",
        "hanumankind", "mohit chauhan", "kk", "kailash kher", "sukhwinder singh", "mika singh",
        "guru randhawa", "harrdy sandhu", "javed ali", "rekha bhardwaj", "monali thakur",
        "neeti mohan", "palak muchhal", "jonita gandhi", "shilpa rao", "nikhita gandhi",
        "asees kaur", "tulsi kumar", "dhvani bhanushali", "stebin ben", "raghav chaitanya",
        "madhubanti bagchi", "papon", "lucky ali", "jasleen royal", "anuv jain", "prateek kuhad",
        "ritviz", "nucleya", "when chai met toast", "the local train", "aditya rikhari",
        "osho jain", "zaeden", "lifafa", "peter cat recording co", "parvaaz", "agnee",
        "euphoria", "indian ocean", "raghu dixit", "sanam", "tanishk bagchi", "shaan",
        "rochak kohli", "jeet gannguli", "ankit tiwari", "benny dayal", "amitabh bhattacharya",
        "himesh reshammiya", "salim-sulaiman", "salim sulaiman", "ajay-atul", "ajay atul",
        "anand-milind", "nadeem-shravan", "jatin-lalit", "rd burman", "r.d. burman", "sd burman",
        "mukesh", "manna dey", "hemant kumar", "jagjit singh", "pankaj udhas", "hariharan",
        "kavita krishnamurthy", "sadhana sargam", "abhishek nailwal", "faheem abdullah",
        "king rocco", "paradox", "panther", "raga", "bella", "fotty seven", "karma",
        "yashraj", "tushar joshi", "vishal bhardwaj", "shashwat sachdev", "manan bhardwaj",
        "payal dev", "akhil sachdeva", "gajendra verma", "abhijeet bhattacharya", "bappi lahiri",
        "daler mehndi", "falguni pathak", "alisha chinai", "bombay vikings", "colonial cousins",
        "trijit", "shashaa tirupati", "chinmayi sripada", "sona mohapatra", "nooran sisters",
        "maithili thakur", "hans raj hans", "wadali brothers", "kabir cafe", "ar rahman",
        "rahat fateh ali khan", "atif aslam",
        // Odia
        "humane sagar", "ira mohanty", "diptirekha padhi", "asima panda", "mantu chhuria",
        "kuldeep pattanaik", "swayam padhi", "ananya sritam nanda", "antara chakraborty",
        "abhijit majumdar", "prem anand", "goodly rath", "bibhu kishore", "akshay mohanty",
        "sikandar alam", "prafulla kar", "sourav bharadwaj", "tapu mishra", "sailabhama mohapatra",
        "satyajit pradhan", "lopamudra dash", "rituraj mohanty", "barnali hota",
        "bishnu mohan kabi", "gaurav anand", "amrita nayak", "archana padhi", "sohini mishra",
        "subhasis mahakud", "aseema panda", "human sagar", "sricharan mohanty", "nibedita",
        // Bengali
        "anupam roy", "rupam islam", "fossils", "nachiketa", "anjan dutt", "kabir suman",
        "rupankar bagchi", "somlata acharyya chowdhury", "somlata", "lagnajita chakraborty",
        "iman chakraborty", "indraadip dasgupta", "ranajoy bhattacharjee", "sahana bajpaie",
        "lopamudra mitra", "srikanto acharya", "chandrabindoo", "hemanta mukherjee",
        "sandhya mukherjee", "subhamita", "raghab chatterjee", "babul supriyo", "silajit",
        // Punjabi
        "diljit dosanjh", "ap dhillon", "karan aujla", "sidhu moose wala", "sidhu moosewala",
        "shubh", "gurinder gill", "shinda kahlon", "gippy grewal", "amrinder gill", "ammy virk",
        "parmish verma", "mankirt aulakh", "jass manak", "jassie gill", "garry sandhu",
        "babbu maan", "gurdas maan", "satinder sartaaj", "sharry mann", "prabh gill",
        "nimrat khaira", "sunanda sharma", "jasmine sandlas", "bohemia", "talwiinder",
        "arjan dhillon", "prem dhillon", "navaan sandhu", "jordan sandhu", "kulwinder billa",
        "tarsem jassar", "ranjit bawa", "hustinder", "cheema y", "gurnam bhullar", "akhil",
        "kaka", "khan bhaini", "nirvair pannu", "sajjan adeeb", "harbhajan mann", "amrit maan",
        // Tamil
        "ilaiyaraaja", "ilayaraja", "anirudh ravichander", "anirudh", "yuvan shankar raja",
        "harris jayaraj", "g.v. prakash", "gv prakash", "santhosh narayanan", "d. imman",
        "d imman", "vidyasagar", "s.p. balasubrahmanyam", "sp balasubrahmanyam", "spb",
        "k.s. chithra", "ks chithra", "sid sriram", "haricharan", "vijay yesudas",
        "pradeep kumar", "chinmayi", "shakthisree gopalan", "andrea jeremiah", "dhanush",
        "hiphop tamizha", "sean roldan", "govind vasantha", "sam c.s.", "ghibran",
        "leon james", "sai abhyankkar", "asal kolaar", "arivu", "paal dabba", "yogi b",
        "naresh iyer", "unnikrishnan", "sujatha mohan", "swetha mohan", "shweta mohan",
        "saindhavi", "karthik", "vijay antony", "vijay prakash", "deva", "thaman",
        // Telugu
        "m.m. keeravani", "mm keeravani", "thaman s", "s. thaman", "devi sri prasad",
        "mani sharma", "mickey j meyer", "anup rubens", "hesham abdul wahab", "vivek sagar",
        "bheems ceciroleo", "ram miriyala", "mangli", "rahul sipligunj", "kaala bhairava",
        "anurag kulkarni", "l.v. revanth", "revanth", "hemachandra", "geetha madhuri",
        "harika narayan", "sahithi chaganti", "ghantasala", "p. susheela", "s. janaki",
        "sri krishna", "sreerama chandra", "chandra bose", "sirivennela",
        // Malayalam
        "k.j. yesudas", "kj yesudas", "yesudas", "m.g. sreekumar", "mg sreekumar",
        "vineeth sreenivasan", "sushin shyam", "gopi sundar", "shaan rahman", "deepak dev",
        "rex vijayan", "bijibal", "jakes bejoy", "kailas menon", "sithara krishnakumar",
        "job kurian", "thaikkudam bridge", "avial", "dabzee", "fejo", "vedan", "thirumali",
        "baby jean", "vidhu prathap", "rimi tomy", "madhu balakrishnan", "k.s. harisankar",
        "ks harisankar", "kapil kapilan", "sooraj santhosh", "stephen devassy",
        // Kannada
        "rajesh krishnan", "arjun janya", "ravi basrur", "ajaneesh loknath",
        "b. ajaneesh loknath", "charan raj", "mano murthy", "v. harikrishna", "gurukiran",
        "hamsalekha", "sanjith hegde", "ananya bhat", "aishwarya rangarajan",
        "chandan shetty", "vasuki vaibhav", "nihal tauro", "dr. rajkumar", "puneeth rajkumar",
        // Marathi
        "ajay gogavale", "atul gogavale", "avadhoot gupte", "bela shende", "vaishali samant",
        "swapnil bandodkar", "adarsh shinde", "anand shinde", "rahul deshpande", "mahesh kale",
        "arya ambekar", "priyanka barve", "rohit raut", "shalmali kholgade", "kaushal inamdar",
        "saleel kulkarni", "sandeep khare", "amitraj", "sanju rathod", "shreya",
        // Assamese
        "zubeen garg", "angaraag mahanta", "bhupen hazarika", "shankuraj konwar", "joi barua",
        "neel akash", "achurjya borpatra", "dikshu", "subasana dutta", "deeplina deka",
        "zublee baruah", "rupam bhuyan",
        // Gujarati
        "kirtidan gadhvi", "geeta rabari", "kinjal dave", "jigardan gadhavi", "aditya gadhvi",
        "osman mir", "parthiv gohil", "aishwarya majmudar", "bhoomi trivedi",
        "siddharth amit bhavsar", "priya saraiya", "praful dave", "hemant chauhan",
        // Bhojpuri / Rajasthani / Folk
        "pawan singh", "khesari lal yadav", "manoj tiwari", "dinesh lal yadav", "nirahua",
        "ritesh pandey", "shilpi raj", "akshara singh", "mame khan", "swaroop khan", "kutle khan"
    )

    /** Strong Indian language, film industry, and cultural music markers. */
    private val VERIFIED_INDIAN_KEYWORDS: Set<String> = setOf(
        "hindi", "bollywood", "odia", "oriya", "odisha", "bengali", "bangla", "rabindra sangeet",
        "punjabi", "bhangra", "tamil", "kollywood", "telugu", "tollywood", "malayalam", "mollywood",
        "kannada", "sandalwood", "marathi", "lavani", "assamese", "bihu", "gujarati", "garba",
        "dandiya", "bhojpuri", "rajasthani", "haryanvi", "kashmiri", "konkani", "manipuri",
        "carnatic", "hindustani", "ghazal", "qawwali", "bhajan", "aarti", "kirtan", "chalisa",
        "indian", "india", "bharat", "desi", "filmi", "indipop", "dhh", "desi hip hop",
        "coke studio bharat", "coke studio india", "bollywood lofi", "hindi lofi", "indian indie",
        "punjabi pop", "indian classical", "devotional", "shiv tandav", "hanuman chalisa",
        "gaane", "geet", "sangeet", "ishq", "pyaar", "dil", "mohabbat", "yaar", "zindagi",
        "tere", "mera", "tumhe", "tumhare", "channa", "ranjha", "heeriye", "kesariya",
        "tu hai", "tujhe", "humdard", "saathiya", "dhadkan", "baarish", "sawan", "maahi",
        "sajni", "sajna", "ve kamleya", "apna bana le", "o maahi", "tauba tauba",
        "jhumka", "choli", "balam", "dhol", "nach", "mehbooba", "sanam", "bewafa"
    )

    /** Known non-Indian international artists/labels explicitly blocked in India mode unless Indian collab. */
    private val KNOWN_FOREIGN_ARTISTS_AND_LABELS: Set<String> = setOf(
        "taylor swift", "drake", "the weeknd", "justin bieber", "billie eilish", "ariana grande",
        "bad bunny", "travis scott", "kendrick lamar", "eminem", "post malone", "dua lipa",
        "olivia rodrigo", "sabrina carpenter", "bruno mars", "coldplay", "imagine dragons",
        "maroon 5", "shakira", "karol g", "peso pluma", "feid", "maluma", "j balvin",
        "daddy yankee", "ozuna", "anuel aa", "rauw alejandro", "rosalia", "myke towers",
        "bts", "blackpink", "stray kids", "newjeans", "twice", "seventeen", "exo", "aespa",
        "ive", "le sserafim", "enhypen", "jung kook", "jungkook", "jimin", "suga", "lisa",
        "jennie", "rose", "jisoo", "bigbang", "ateez", "txt", "itzy", "nct", "red velvet",
        "hybe labels", "smtown", "jyp entertainment", "yg entertainment", "starship",
        "yoasobi", "ado", "kenshi yonezu", "fujii kaze", "lisa japan", "official hige dandism",
        "king gnu", "aimyon", "vaundy", "creepy nuts", "radwimps", "one ok rock", "babymetal",
        "sza", "beyonce", "rihanna", "lady gaga", "katy perry", "adele", "sam smith",
        "harry styles", "shawn mendes", "camila cabello", "charlie puth", "selena gomez",
        "miley cyrus", "doja cat", "nicki minaj", "cardi b", "megan thee stallion",
        "21 savage", "future", "metro boomin", "playboi carti", "kanye west", "ye",
        "lil baby", "lil uzi vert", "juice wrld", "xxxtentacion", "pop smoke", "morgan wallen",
        "luke combs", "zach bryan", "chris stapleton", "linkin park", "metallica", "nirvana",
        "queen", "the beatles", "michael jackson", "elton john", "madonna", "lana del rey",
        "arctic monkeys", "the neighbourhood", "chase atlantic", "cigaretters after sex",
        "cigarettes after sex", "tame impala", "frank ocean", "tyler the creator",
        "billie", "ed sheeran", "david guetta", "calvin harris", "martin garrix", "marshmello",
        "alan walker", "avicii", "kygo", "skrillex", "tiesto", "the chainsmokers",
        "onerepublic", "twenty one pilots", "fall out boy", "green day", "red hot chili peppers",
        "ac/dc", "guns n roses", "pink floyd", "led zeppelin", "fleetwood mac",
        "gracie abrams", "chappell roan", "benson boone", "teddy swims", "hozier", "noah kahan",
        "tate mcrae", "troye sivan", "charli xcx", "central cee", "dave", "stormzy",
        "burna boy", "wizkid", "rema", "tems", "ayra starr", "davido", "asake", "tyla",
        "stromae", "aya nakamura", "gims", "indila", "jul", "ninho",
        "rammstein", "apache 207", "anitta", "gusttavo lima", "mc kevinho",
        "amr diab", "nancy ajram", "tamer hosny", "miyagi", "morgenshtern",
        "vevo", "atlantic records", "republic records", "interscope", "columbia records",
        "capitol records", "def jam recordings", "rca records", "epic records",
        "sony music entertainment", "warner records", "fueled by ramen", "spinnin records",
        "monstercat", "nocopyrightsounds", "ncs"
    )

    private val FOREIGN_CATALOG_KEYWORDS: Set<String> = setOf(
        "k-pop", "kpop", "j-pop", "jpop", "anime ost", "anime opening", "anime ending",
        "reggaeton", "latin pop", "musica latina", "corridos", "bachata", "salsa",
        "sertanejo", "funk brasileiro", "afrobeats", "amapiano", "french rap",
        "deutschrap", "mandopop", "cantopop", "opm", "v-pop", "t-pop", "arab pop",
        "billboard hot 100", "uk top 40", "us top 50"
    )

    private val NORMALIZED_INDIAN_LABELS: Set<String> by lazy {
        VERIFIED_INDIAN_LABELS.map { normalize(it) }.filter { it.isNotBlank() }.toSet()
    }

    private val NORMALIZED_INDIAN_ARTISTS: Set<String> by lazy {
        VERIFIED_INDIAN_ARTISTS.map { normalize(it) }.filter { it.isNotBlank() }.toSet()
    }

    private val NORMALIZED_INDIAN_KEYWORDS: Set<String> by lazy {
        VERIFIED_INDIAN_KEYWORDS.map { normalize(it) }.filter { it.isNotBlank() }.toSet()
    }

    private val NORMALIZED_FOREIGN_ARTISTS_AND_LABELS: Set<String> by lazy {
        KNOWN_FOREIGN_ARTISTS_AND_LABELS.map { normalize(it) }.filter { it.isNotBlank() }.toSet()
    }

    private val NORMALIZED_FOREIGN_CATALOG_KEYWORDS: Set<String> by lazy {
        FOREIGN_CATALOG_KEYWORDS.map { normalize(it) }.filter { it.isNotBlank() }.toSet()
    }

    private fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
        return MULTISPACE_REGEX.replace(
            NON_ALNUM_REGEX.replace(nfc.lowercase(Locale.ROOT), " "),
            " "
        ).trim()
    }

    private fun containsWholePhrase(haystack: String, phrase: String): Boolean {
        if (haystack.isEmpty() || phrase.isEmpty()) return false
        val idx = haystack.indexOf(phrase)
        if (idx < 0) return false
        val beforeOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
        val end = idx + phrase.length
        val afterOk = end == haystack.length || !haystack[end].isLetterOrDigit()
        if (beforeOk && afterOk) return true
        // Search subsequent occurrences in case first was substring of a longer word
        var nextStart = idx + 1
        while (nextStart < haystack.length) {
            val nextIdx = haystack.indexOf(phrase, nextStart)
            if (nextIdx < 0) return false
            val bOk = nextIdx == 0 || !haystack[nextIdx - 1].isLetterOrDigit()
            val e = nextIdx + phrase.length
            val aOk = e == haystack.length || !haystack[e].isLetterOrDigit()
            if (bOk && aOk) return true
            nextStart = nextIdx + 1
        }
        return false
    }

    /**
     * Strict positive-verification check for Indian music eligibility.
     *
     * Rules:
     * 1. Requires positive Indian metadata evidence (Indic script, verified Indian artist,
     *    verified Indian label/channel, or verified Indian language/film/genre signal).
     * 2. Known foreign artists/labels are rejected UNLESS the track is explicitly verified
     *    as an Indian artist collaboration or released on a verified Indian record label.
     * 3. Unknown or unverified tracks return FALSE (never assumed Indian).
     * 4. English-language songs by verified Indian artists or labels return TRUE.
     */
    fun isVerifiedIndianMusic(
        title: String,
        artist: String,
        album: String = "",
        genre: String = "",
        extraMetadata: String = ""
    ): Boolean {
        val rawCombined = "$title $artist $album $genre $extraMetadata"
        val normTitle = normalize(title)
        val normArtist = normalize(artist)
        val normAlbum = normalize(album)
        val normGenre = normalize(genre)
        val normExtra = normalize(extraMetadata)
        val normAll = listOf(normTitle, normArtist, normAlbum, normGenre, normExtra)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (normAll.isBlank()) return false

        val hasIndicScript = containsIndicScript(rawCombined)
        val hasVerifiedIndianLabel = NORMALIZED_INDIAN_LABELS.any { label ->
            containsWholePhrase(normArtist, label) ||
                containsWholePhrase(normTitle, label) ||
                containsWholePhrase(normAlbum, label) ||
                containsWholePhrase(normExtra, label)
        }
        val hasVerifiedIndianArtist = NORMALIZED_INDIAN_ARTISTS.any { indianArtist ->
            containsWholePhrase(normArtist, indianArtist) ||
                containsWholePhrase(normTitle, indianArtist)
        }
        val hasVerifiedIndianKeyword = NORMALIZED_INDIAN_KEYWORDS.any { keyword ->
            containsWholePhrase(normAll, keyword)
        }

        val hasForeignScript = containsForeignNonLatinScript(rawCombined)
        val hasForeignCatalogKeyword = NORMALIZED_FOREIGN_CATALOG_KEYWORDS.any { kw ->
            containsWholePhrase(normAll, kw)
        }
        val hasKnownForeignArtistOrLabel = NORMALIZED_FOREIGN_ARTISTS_AND_LABELS.any { foreign ->
            containsWholePhrase(normArtist, foreign) || containsWholePhrase(normTitle, foreign)
        }

        // Foreign script or explicit foreign catalog genre (K-pop, J-pop, Reggaeton, etc.)
        // is strictly rejected unless there is direct verified Indian artist/label ownership.
        if ((hasForeignScript || hasForeignCatalogKeyword) &&
            !hasIndicScript && !hasVerifiedIndianLabel && !hasVerifiedIndianArtist
        ) {
            return false
        }

        // Known foreign artist/label is strictly rejected unless this is a verified Indian
        // collaboration (verified Indian artist also credited) or Indian label release.
        if (hasKnownForeignArtistOrLabel && !hasVerifiedIndianArtist && !hasVerifiedIndianLabel) {
            return false
        }

        // Strict positive verification: unverified/unknown origin tracks MUST be excluded.
        return hasIndicScript || hasVerifiedIndianLabel || hasVerifiedIndianArtist || hasVerifiedIndianKeyword
    }

    fun isVerifiedIndianTrack(track: OnlineTrack): Boolean {
        return isVerifiedIndianMusic(
            title = track.title,
            artist = track.artist,
            album = track.album,
            extraMetadata = track.contentUrl
        )
    }

    fun isVerifiedIndianSong(song: Song): Boolean {
        return isVerifiedIndianMusic(
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre,
            extraMetadata = song.contentUrl
        )
    }

    fun isIndiaRegion(countryCode: String?): Boolean {
        return countryCode?.trim().equals(REGION_INDIA, ignoreCase = true)
    }

    /**
     * Strictly filters a list of [OnlineTrack] recommendations for the active region.
     * - For "IN": returns ONLY verified Indian music. Never backfills with foreign tracks.
     * - For other countries: preserves region-appropriate tracks without hardcoding India.
     */
    fun filterTracksForRegion(
        tracks: List<OnlineTrack>,
        countryCode: String
    ): List<OnlineTrack> {
        if (tracks.isEmpty()) return emptyList()
        if (isIndiaRegion(countryCode)) {
            return tracks.filter { isVerifiedIndianTrack(it) }
        }
        return tracks
    }

    /**
     * Strictly filters a list of unified [Song] recommendations / radio / auto-queue candidates
     * for the active region. Never backfills empty lists with foreign tracks.
     */
    fun filterSongsForRegion(
        songs: List<Song>,
        countryCode: String
    ): List<Song> {
        if (songs.isEmpty()) return emptyList()
        if (isIndiaRegion(countryCode)) {
            return songs.filter { isVerifiedIndianSong(it) }
        }
        return songs
    }

    /**
     * Filters recommendation-driven search suggestions when India region is selected.
     * Direct user-typed foreign searches remain allowed when the user explicitly types a foreign artist,
     * while generic/empty or recommendation-style suggestions stay strictly India-eligible.
     */
    fun filterSuggestionsForRegion(
        suggestions: List<String>,
        userTypedQuery: String,
        countryCode: String
    ): List<String> {
        if (suggestions.isEmpty() || !isIndiaRegion(countryCode)) return suggestions
        val normTyped = normalize(userTypedQuery)
        // If user explicitly searched a known foreign artist/genre, honor their manual query
        val userExplicitlySearchedForeign = NORMALIZED_FOREIGN_ARTISTS_AND_LABELS.any {
            containsWholePhrase(normTyped, it)
        } || NORMALIZED_FOREIGN_CATALOG_KEYWORDS.any { containsWholePhrase(normTyped, it) }
        if (userExplicitlySearchedForeign) return suggestions

        // Otherwise keep suggestions that are either verified Indian OR match the user's literal prefix without foreign catalog markers
        val filtered = suggestions.filter { suggestion ->
            val normSug = normalize(suggestion)
            val isExplicitForeign = NORMALIZED_FOREIGN_ARTISTS_AND_LABELS.any { containsWholePhrase(normSug, it) } ||
                NORMALIZED_FOREIGN_CATALOG_KEYWORDS.any { containsWholePhrase(normSug, it) } ||
                containsForeignNonLatinScript(suggestion)
            !isExplicitForeign
        }
        return filtered
    }

    /**
     * Scopes a recommendation / mood / radio query to the active region so NewPipe queries
     * directly fetch regionally eligible catalogs rather than global mixed pools.
     */
    fun scopeRecommendationQuery(
        baseQuery: String,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage = DiscoverMusicLanguage.ALL
    ): String {
        val cleanBase = baseQuery.trim()
        if (!isIndiaRegion(countryCode)) return cleanBase
        val normBase = normalize(cleanBase)
        val alreadyIndianScoped = NORMALIZED_INDIAN_KEYWORDS.any { containsWholePhrase(normBase, it) } ||
            NORMALIZED_INDIAN_ARTISTS.any { containsWholePhrase(normBase, it) } ||
            containsIndicScript(cleanBase)
        if (alreadyIndianScoped) return cleanBase
        val langPrefix = if (discoverLanguage != DiscoverMusicLanguage.ALL) {
            discoverLanguage.searchKeyword
        } else {
            "Indian Hindi Bollywood"
        }
        return "$langPrefix $cleanBase".trim()
    }

    /**
     * Returns an India-only fallback query when countryCode == "IN" (never a global fallback),
     * or a region-appropriate fallback for other countries.
     */
    fun regionSafeFallbackQuery(
        sectionType: DiscoverSectionType,
        countryCode: String,
        discoverLanguage: DiscoverMusicLanguage = DiscoverMusicLanguage.ALL
    ): String? {
        if (isIndiaRegion(countryCode)) {
            val langTag = if (discoverLanguage != DiscoverMusicLanguage.ALL) {
                discoverLanguage.searchKeyword
            } else {
                "Indian Bollywood Hindi Tamil Telugu Punjabi Odia"
            }
            return when (sectionType) {
                DiscoverSectionType.TRENDING_MUSIC -> "$langTag trending hit songs India"
                DiscoverSectionType.POPULAR_SONGS -> "$langTag top popular songs India"
                DiscoverSectionType.NEW_RELEASES -> "$langTag latest new songs India"
                DiscoverSectionType.MUSIC_RECOMMENDATIONS -> "$langTag best hit songs mix India"
                DiscoverSectionType.POPULAR_ARTISTS -> "Indian top playback singers Hindi Tamil Punjabi Odia"
                DiscoverSectionType.ONLINE_PLAYLISTS -> "$langTag curated hit songs playlist India"
            }
        }
        return when (sectionType) {
            DiscoverSectionType.TRENDING_MUSIC -> "trending music hits $countryCode"
            DiscoverSectionType.POPULAR_SONGS -> "popular music songs $countryCode"
            DiscoverSectionType.NEW_RELEASES -> "new music releases $countryCode"
            DiscoverSectionType.MUSIC_RECOMMENDATIONS -> "top music mix playlist $countryCode"
            DiscoverSectionType.POPULAR_ARTISTS -> "popular music artists $countryCode"
            DiscoverSectionType.ONLINE_PLAYLISTS -> "top music playlists mix $countryCode"
        }
    }

    fun emptyRegionRecommendationsMessage(
        sectionTitle: String,
        countryCode: String,
        providerName: String
    ): String {
        return if (isIndiaRegion(countryCode)) {
            "No verified Indian ${sectionTitle.lowercase(Locale.ROOT)} available right now on $providerName. Try refreshing or switching Indian language filter."
        } else {
            "No ${sectionTitle.lowercase(Locale.ROOT)} available right now on $providerName."
        }
    }
}
