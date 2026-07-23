package com.back.global.initData.fixture

import com.back.domain.concert.entity.Concert
import com.back.domain.concert.entity.ConcertDetail
import com.back.domain.concert.repository.ConcertDeatilRepository
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class ConcertDetailFixture(
    private val concertDeatilRepository: ConcertDeatilRepository
) {
    fun createDetails(concerts: List<Concert>): List<ConcertDetail> {
        val details = mutableListOf<ConcertDetail>()

        for (concert in concerts) {
            val mt20Id = extractMt20Id(concert.urlPoster) ?: continue

            val matchedFiles = DETAIL_IMAGE_FILES
                .filter { filename -> mt20Id == extractMt20Id(filename) }
                .sorted()

            for (filename in matchedFiles) {
                details.add(ConcertDetail.create(concert, BASE_URL + filename))
            }
        }

        return concertDeatilRepository.saveAll(details)
    }

    private fun extractMt20Id(text: String?): String? {
        if (text == null) return null
        val matcher = MT20ID_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    companion object {
        private const val BASE_URL = "/images/concerts/"
        private val MT20ID_PATTERN: Pattern = Pattern.compile("(PF\\d+)")

        private val DETAIL_IMAGE_FILES = listOf(
            "PF232456.png",
            "PF232467.png",
            "PF232471.png",
            "PF232473.png",
            "PF233707.png",
            "PF234436.png",
            "PF235543.png",
            "PF241378.png",
            "PF244704.png",
            "PF271736.png",
            "PF282011.png",
            "PF_PF282014_202512220548232960.webp",
            "PF282015.png",
            "PF282016.png",
            "PF282031.png",
            "PF282176.png",
            "PF283207.png",
            "PF283793.png",
            "PF_PF283878_202601260548562720.webp",
            "PF294720.png",
            "PF294721.png",
            "PF294722.png",
            "PF_PF294723_202606260249097210.webp",
            "PF_PF294724_202606260254106290.webp",
            "PF294726.png",
            "PF294727.png",
            "PF294728.png",
            "PF294729.png",
            "PF294730.png"
        )
    }
}
