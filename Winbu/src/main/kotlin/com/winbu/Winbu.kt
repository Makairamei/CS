package com.winbu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore  
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Winbu : MainAPI() {
    override var mainUrl = "https://winbu.net"
    override var name = "Winbu"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/film/" to "Film Terbaru",
        "$mainUrl/anime-terbaru-animasu/" to "Series Terbaru",
        "$mainUrl/animedonghua/" to "Animasi Terbaru",
    )

    private fun pagedUrl(baseUrl: String, page: Int): String {
        return if (page <= 1) baseUrl else "${baseUrl.trimEnd('/')}/page/$page/"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = app.get(pagedUrl(request.data, page)).document

    val items = document.select(".movies-list .ml-item").mapNotNull {
        it.toSearchResult(request.name)
    }

    return newHomePageResponse(
        list = HomePageList(
            name = request.name,
            list = items
        ),
        hasNext = items.isNotEmpty()
    )
}


    private fun parseEpisode(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return Regex("(\\d+[\\.,]?\\d*)").find(text)?.groupValues?.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.toInt()
    }

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, "UTF-8")

    private fun Element.toSearchResult(sectionName: String): SearchResponse? {
    val anchor = selectFirst("a.ml-mask") ?: selectFirst("a[href]") ?: return null
    val href = fixUrl(anchor.attr("href"))

    val title = anchor.attr("title").ifBlank {
        selectFirst("div.judul")?.text().orEmpty()
    }.ifBlank {
        selectFirst("img.mli-thumb, img")?.attr("alt").orEmpty()
    }.trim()

    if (title.isBlank()) return null

    val poster = selectFirst("img.mli-thumb, img")?.getImageAttr()?.let { fixUrlNull(it) }

    val isFilm = sectionName.contains("Film", ignoreCase = true) || href.contains("/film/", ignoreCase = true)

    return if (isFilm) {
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    } else {
        val episodeText =
            selectFirst("span.mli-episode")?.text()
                ?: selectFirst("span.mli-info span")?.text()
        val episode = parseEpisode(episodeText)

        newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
            if (episode != null) addSub(episode)
        }
    }
}

    override suspend fun search(query: String): List<SearchResponse> {
    val document = app.get("$mainUrl/?s=${query.urlEncoded()}").document
    val results = document.select(".movies-list a[href]") 
        .mapNotNull { a ->
            val parent = a.closest("article, .ml-item, div") ?: return@mapNotNull null
            parent.toSearchResult("Series")
        }
        .distinctBy { it.url }
    return results
}

    private fun cleanupTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("^Nonton\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+Sub\\s+Indo.*$", RegexOption.IGNORE_CASE), "")
            .replace(" - Winbu", "", ignoreCase = true)
            .trim()
    }

    override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val rawTitle = document.selectFirst("h1")?.text()
        ?: document.selectFirst("div.judul")?.text()
        ?: document.selectFirst("meta[property=\"og:title\"]")?.attr("content")
        ?: "No Title"
    val title = cleanupTitle(rawTitle)

    val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")
        ?: document.selectFirst("img")?.getImageAttr()

    val description =
        document.selectFirst("div.mli-desc p")?.text()?.trim()
            ?: document.selectFirst("meta[name=\"description\"]")?.attr("content")
            ?: document.select("p").firstOrNull { it.text().length > 60 }?.text()

    val tags = document.select("a[rel=tag]").map { it.text().trim() }.distinct()

    val recs = document.select("#randomList a, .movies-list .ml-item, .movies-list .t-item")
        .mapNotNull { el ->
            if (el.tagName() == "a") {
                val href = el.attr("href").trim()
                val recTitle = el.attr("title").ifBlank { el.text() }.trim()
                val recPoster = el.selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
                if (href.isBlank() || recTitle.isBlank()) null
                else newMovieSearchResponse(recTitle, fixUrl(href), TvType.Movie) {
                    this.posterUrl = recPoster
                }
            } else {
                el.toSearchResult("Film")
            }
        }
        .distinctBy { it.url }

    val episodeLinks = document.select("div.tvseason div.les-content a[href]")
        .mapNotNull { a ->
            val href = a.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            val epText = a.text().trim()
            val epNum = parseEpisode(epText)
            newEpisode(fixUrl(href)) {
                this.name = epText
                this.episode = epNum
            }
        }
        .distinctBy { it.data }
        .reversed()

    val isFilm = url.contains("/film/", ignoreCase = true)
    val isSeries = !isFilm && episodeLinks.isNotEmpty()

    return if (isSeries) {
        newTvSeriesLoadResponse(title, url, TvType.Anime, episodeLinks) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recs
        }
    } else {
        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recs
        }
    }
}



    private fun Element.getImageAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }

    private fun Element.getIframeAttr(): String? {
        return attr("data-litespeed-src").takeIf { it.isNotBlank() }
            ?: attr("data-src").takeIf { it.isNotBlank() }
            ?: attr("src").takeIf { it.isNotBlank() }
    }
}
