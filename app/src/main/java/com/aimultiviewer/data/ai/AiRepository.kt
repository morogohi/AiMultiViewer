package com.aimultiviewer.data.ai

import com.aimultiviewer.data.SettingsStore

/**
 * AI 기능 오케스트레이션.
 * - 요약: 온디바이스(기본) 또는 클라우드(고품질)
 * - 질의응답/비교/회의록/기술분석: 클라우드 LLM 사용(설정 필요)
 */
class AiRepository(
    private val settings: SettingsStore,
    private val llm: LlmClient
) {
    private val maxContext = 12_000

    suspend fun summarize(text: String): String {
        if (text.isBlank()) return "요약할 본문이 없습니다."
        return if (settings.isCloudReady) {
            llm.chat(
                system = "당신은 한국어 문서 요약 전문가입니다. 핵심만 간결한 불릿으로 요약하세요.",
                user = "다음 문서를 5~7개의 핵심 불릿으로 요약해줘.\n\n" + text.take(maxContext)
            )
        } else {
            Summarizer.summarize(text, maxSentences = 6) +
                "\n\n(온디바이스 요약 결과 · 더 정확한 요약은 설정에서 클라우드 AI를 켜세요)"
        }
    }

    suspend fun ask(question: String, context: String): String {
        if (!settings.isCloudReady) return cloudRequiredMessage("질의응답")
        return llm.chat(
            system = "문서 내용에 근거해서만 한국어로 답하세요. 모르면 모른다고 답하세요.",
            user = "문서:\n${context.take(maxContext)}\n\n질문: $question"
        )
    }

    suspend fun compare(a: String, b: String): String {
        if (!settings.isCloudReady) return cloudRequiredMessage("문서 비교")
        return llm.chat(
            system = "두 문서를 비교 분석하는 전문가입니다. 한국어로 답하세요.",
            user = "두 문서의 공통점, 차이점, 주요 변경사항을 표 형식으로 정리해줘.\n\n" +
                "[문서 A]\n${a.take(maxContext / 2)}\n\n[문서 B]\n${b.take(maxContext / 2)}"
        )
    }

    suspend fun minutes(text: String): String {
        if (!settings.isCloudReady) return cloudRequiredMessage("회의록 생성")
        return llm.chat(
            system = "회의록 작성 전문가입니다. 한국어로 답하세요.",
            user = "다음 내용을 바탕으로 회의록을 작성해줘. 항목: 1) 안건 2) 주요 논의 3) 결정사항 " +
                "4) 액션아이템(담당/기한 포함, 추정 가능 시).\n\n" + text.take(maxContext)
        )
    }

    suspend fun analyzeTechDoc(text: String): String {
        if (!settings.isCloudReady) return cloudRequiredMessage("기술문서 분석")
        return llm.chat(
            system = "기술문서 분석가입니다. 한국어로 답하세요.",
            user = "다음 기술문서를 분석해줘. 항목: 1) 한 줄 개요 2) 핵심 개념/용어 3) 아키텍처/구성요소 " +
                "4) 잠재적 이슈/주의점.\n\n" + text.take(maxContext)
        )
    }

    private fun cloudRequiredMessage(feature: String): String =
        "‘$feature’ 기능은 클라우드 AI가 필요합니다.\n설정 화면에서 클라우드 AI를 켜고 API 키를 입력해주세요."
}
