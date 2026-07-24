package com.back.global.initData.fixture

import com.back.domain.concert.entity.Concert
import com.back.domain.concert.repository.ConcertRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ConcertFixture(
    private val concertRepository: ConcertRepository
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun dt(value: String): LocalDateTime = LocalDateTime.parse(value, fmt)

    fun createConcerts(): List<Concert> {
        val concerts = listOf(
            Concert.create("부산시립국악관현악단, 다함께 행복한 음악회: 얼씨구", "부산시립국악관현악단이 선보이는 국악 공연으로, 전통음악의 흥과 현대적인 감성을 함께 느낄 수 있는 무대입니다.", dt("2026-06-16 00:00:00"), dt("2026-07-22 18:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF244704_240710_140351.jpg"),
            Concert.create("쉬어매드니스 [대학로]", "관객의 선택과 추리를 통해 매 회차 다른 결말을 만들어가는 대학로 대표 참여형 코미디 추리극입니다.", dt("2026-06-26 00:00:00"), dt("2026-08-07 20:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF241378_260618_130555.gif"),
            Concert.create("난타 [명동]", "주방을 배경으로 펼쳐지는 리듬 퍼포먼스 공연으로, 타악과 코미디가 어우러진 명동 대표 넌버벌 공연입니다.", dt("2026-06-25 00:00:00"), dt("2026-07-20 18:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF235543_251014_140238.gif"),
            Concert.create("파이팅 콘서트 시즌Ⅳ, 무직회사", "지친 일상에 웃음과 응원을 전하는 콘서트형 공연으로, 유쾌한 음악과 메시지를 함께 담은 무대입니다.", dt("2026-06-11 00:00:00"), dt("2026-08-20 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF234436_240124_160622.gif"),
            Concert.create("제31회 젊은연극제, 한씨연대기 [청주]", "젊은 창작자들의 시선으로 풀어낸 연극 작품으로, 한국 현대사의 흐름과 개인의 삶을 무대 위에 담아냅니다.", dt("2026-06-16 00:00:00"), dt("2026-06-30 16:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF233707_240110_134155.jpg"),
            Concert.create("Academic Guitar Festival, 황민웅 독주회", "섬세한 클래식 기타 선율을 중심으로 구성된 독주회로, 기타 음악의 깊이와 매력을 감상할 수 있는 공연입니다.", dt("2026-06-18 00:00:00"), dt("2026-09-09 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF232473_231213_194942.gif"),
            Concert.create("잠자는 숲속의 공주 [대구]", "동화 속 이야기를 무대 위에 옮긴 가족 공연으로, 어린이와 가족 관객이 함께 즐기기 좋은 작품입니다.", dt("2026-07-01 00:00:00"), dt("2026-07-30 16:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF232471_231213_190327.gif"),
            Concert.create("콘크리트 보이스 1: 천변우로 415", "도시 공간과 사람들의 목소리를 소재로 한 연극으로, 현실적인 감각과 실험적인 무대 구성이 돋보이는 작품입니다.", dt("2026-06-21 00:00:00"), dt("2026-07-05 18:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF232467_231213_173408.jpg"),
            Concert.create("춤과 인문학: Part.1, 춤; 움직임, 숨", "춤과 인문학을 연결해 몸의 움직임과 호흡이 지닌 의미를 탐색하는 무용 기반 공연입니다.", dt("2026-07-06 00:00:00"), dt("2026-12-11 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF232456_231213_161929.jpg"),
            Concert.create("월드아트 서커스 [당진]", "다채로운 곡예와 퍼포먼스를 통해 남녀노소 모두가 즐길 수 있는 서커스 공연입니다.", dt("2026-07-02 00:00:00"), dt("2027-02-24 17:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF282176_251224_145437.jpg"),
            Concert.create("개미와 베짱이 [인천]", "익숙한 우화를 바탕으로 성실함과 즐거움의 의미를 어린이 눈높이에 맞춰 풀어낸 가족 뮤지컬입니다.", dt("2026-05-06 00:00:00"), dt("2026-07-01 16:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF271736_250814_140336.jpg"),
            Concert.create("카디션 김슬기의 다윈의 역작 (7월)", "마술과 이야기가 결합된 공연으로, 관객에게 신비로운 장면과 색다른 상상력을 선사하는 무대입니다.", dt("2026-06-11 00:00:00"), dt("2026-07-30 20:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF283878_260126_175033.png"),
            Concert.create("장애인문화예술축제 A+ Festival", "장애 예술인의 창작 활동과 다양한 문화예술 콘텐츠를 함께 만날 수 있는 복합 문화예술 축제입니다.", dt("2026-07-01 00:00:00"), dt("2026-10-03 18:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF283793_260123_154747.png"),
            Concert.create("Love & Jazz (발렌타인&화이트 데이) [사당]", "사랑을 주제로 한 재즈 공연으로, 감미로운 연주와 로맨틱한 분위기를 즐길 수 있는 무대입니다.", dt("2026-03-10 00:00:00"), dt("2027-02-24 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF283207_260115_101035.png"),
            Concert.create("오잉클!", "클래식 음악을 쉽고 재미있게 경험할 수 있도록 구성한 공연으로, 어린이와 가족 관객에게 어울리는 무대입니다.", dt("2026-04-08 00:00:00"), dt("2026-09-30 15:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF282031_260113_150927.jpg"),
            Concert.create("해리엇 드레스 리허설", "공연 준비 과정과 무대 뒤 이야기를 연극적으로 풀어낸 작품으로, 인물의 감정과 관계를 섬세하게 그려냅니다.", dt("2026-05-28 00:00:00"), dt("2026-08-25 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF282016_251222_175849.jpg"),
            Concert.create("드림시어터 (DREAM THEATER)", "꿈과 현실이 교차하는 무대 설정을 통해 인물들의 이야기를 감각적으로 표현한 연극입니다.", dt("2026-05-28 00:00:00"), dt("2026-07-11 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF282015_251222_174823.jpg"),
            Concert.create("금정클래식위크 2nd. 이음: 금빛누리홀 (패키지)", "클래식 음악을 다양한 프로그램으로 만날 수 있는 패키지 공연으로, 풍성한 연주 무대를 제공합니다.", dt("2026-07-01 00:00:00"), dt("2026-08-30 18:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF282014_251222_174823.jpeg"),
            Concert.create("위클리 클래식 페스티벌, 카메라타 필 앙상블: America 미국 [청주]", "미국을 주제로 한 클래식 앙상블 공연으로, 다채로운 음악적 색채를 감상할 수 있는 무대입니다.", dt("2026-07-09 00:00:00"), dt("2026-07-23 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF282011_251222_172213.jpg"),
            Concert.create("먼데이프로젝트 시즌9, OUR SUMMER: 심재현 X 히미츠", "여름의 분위기를 담은 라이브 공연으로, 심재현과 히미츠의 감성적인 음악을 가까이에서 만날 수 있는 무대입니다.", dt("2026-06-16 00:00:00"), dt("2026-07-23 20:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294730_260626_152625.jpg"),
            Concert.create("나상현씨밴드 클럽투어: 여름빛 [전주]", "나상현씨밴드의 에너지와 여름 감성을 담은 클럽 투어 공연으로, 전주에서 생생한 라이브를 즐길 수 있습니다.", dt("2026-07-01 00:00:00"), dt("2026-09-02 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294729_260626_152007.jpg"),
            Concert.create("nib.archive 전국 클럽투어: idea of nib [광주]", "nib.archive의 음악적 색깔을 라이브로 선보이는 전국 클럽투어 공연으로, 광주 관객과 만나는 무대입니다.", dt("2026-07-29 00:00:00"), dt("2026-08-12 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294728_260626_151356.png"),
            Concert.create("백설공주 [서울 노원]", "고전 동화 백설공주를 무대화한 가족 뮤지컬로, 친숙한 이야기와 생동감 있는 무대를 함께 즐길 수 있습니다.", dt("2026-07-08 00:00:00"), dt("2026-09-01 16:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294727_260626_150658.jpg"),
            Concert.create("제44회 대한민국 연극제 부산, 모스크바의 바다", "대한민국 연극제에서 선보이는 연극 작품으로, 깊이 있는 서사와 배우들의 밀도 높은 연기를 만날 수 있습니다.", dt("2026-06-27 00:00:00"), dt("2026-07-29 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294726_260626_150019.png"),
            Concert.create("제24회 김천 국제 가족 연극제, 빨래터에 봄이 오면", "가족 관객이 함께 즐길 수 있는 연극제 작품으로, 따뜻한 정서와 생활 속 이야기를 무대에 담았습니다.", dt("2026-07-06 00:00:00"), dt("2026-08-05 16:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294724_260626_145410.jpg"),
            Concert.create("제24회 김천 국제 가족 연극제, 봄날은 간다", "가족과 함께 감상하기 좋은 연극제 공연으로, 계절의 정서와 삶의 이야기를 서정적으로 풀어냅니다.", dt("2026-07-27 00:00:00"), dt("2026-08-10 15:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294723_260626_144909.jpg"),
            Concert.create("먼데이프로젝트 IN 라이브클럽, 물결 단독콘서트", "밴드 물결의 음악을 라이브클럽에서 가까이 즐길 수 있는 단독 콘서트입니다.", dt("2026-07-12 00:00:00"), dt("2026-07-26 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294722_260626_144805.png"),
            Concert.create("나상현씨밴드 클럽투어: 여름빛 [춘천]", "나상현씨밴드의 클럽투어 춘천 공연으로, 밴드 특유의 청량한 사운드와 라이브 에너지를 느낄 수 있습니다.", dt("2026-07-16 00:00:00"), dt("2026-09-08 19:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294721_260626_144209.png"),
            Concert.create("씨야 20주년 전국 투어 콘서트: THE FAN [대구]", "씨야의 데뷔 20주년을 기념하는 전국 투어 콘서트로, 팬들과 함께 대표곡과 추억을 나누는 무대입니다.", dt("2026-07-08 00:00:00"), dt("2026-09-22 20:00:00"), "http://www.kopis.or.kr/upload/pfmPoster/PF_PF294720_260626_144208.jpg")
        )
        return concertRepository.saveAll(concerts)
    }
}
