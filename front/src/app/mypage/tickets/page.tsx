"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ChevronLeft, Printer } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { getLocalConcertPoster } from "@/lib/concertDetailImages";

interface TicketSummary {
  ticketId: number;
  ticketNumber: string;
  qrToken?: string;
  seatNumber: string;
  gradeName: string;
  ticketPrice: number;
  isValid: boolean;
  createdAt: string;
}

interface TicketGroupInfo {
  scheduleId: number;
  concertName: string;
  urlPoster: string;
  startDate: string;
  endDate: string;
  round: number;
  totalPrice: number;
  tickets: TicketSummary[];
}

function parseGroup(raw: string | null): TicketGroupInfo | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(decodeURIComponent(raw));
    return parsed && Array.isArray(parsed.tickets) ? parsed : null;
  } catch {
    return null;
  }
}

function TicketDetailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [group] = useState<TicketGroupInfo | null>(() => parseGroup(searchParams.get("group")));
  const [isFlipped, setIsFlipped] = useState(false);
  const [origin, setOrigin] = useState("");

  useEffect(() => {
    setOrigin(window.location.origin);
  }, []);

  if (!group) {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center gap-4">
        <p className="text-gray-400">티켓 정보를 찾을 수 없습니다.</p>
        <Link href="/mypage" className="text-blue-600 font-semibold hover:underline">
          마이페이지로 돌아가기
        </Link>
      </div>
    );
  }

  const allInvalid = group.tickets.every((t) => !t.isValid);
  const statusLabel = allInvalid ? "취소됨" : "예매완료";

  return (
    <div className="min-h-screen bg-gray-50 p-10">
      {/* 진짜 영화 티켓처럼: 네 모서리는 둥글게, 위/아래 테두리는 올록볼록한 절취선 홈으로 마스킹한다.
          인쇄할 때는 화면 전용 UI를 숨기고, 뒤집기(3D transform)도 풀어서 앞/뒷면이 순서대로 나오게 한다. */}
      <style>{`
        .ticket-face-mask {
          mask-image:
            radial-gradient(circle at 0 0, transparent 18px, #000 19px),
            radial-gradient(circle at 100% 0, transparent 18px, #000 19px),
            radial-gradient(circle at 0 100%, transparent 18px, #000 19px),
            radial-gradient(circle at 100% 100%, transparent 18px, #000 19px),
            radial-gradient(circle at 50% -2px, transparent 6px, #000 7px),
            radial-gradient(circle at 50% calc(100% + 2px), transparent 6px, #000 7px);
          mask-composite: intersect;
          -webkit-mask-image:
            radial-gradient(circle at 0 0, transparent 18px, #000 19px),
            radial-gradient(circle at 100% 0, transparent 18px, #000 19px),
            radial-gradient(circle at 0 100%, transparent 18px, #000 19px),
            radial-gradient(circle at 100% 100%, transparent 18px, #000 19px),
            radial-gradient(circle at 50% -2px, transparent 6px, #000 7px),
            radial-gradient(circle at 50% calc(100% + 2px), transparent 6px, #000 7px);
          -webkit-mask-composite: source-in;
          mask-size: 100% 100%, 100% 100%, 100% 100%, 100% 100%, 20px 100%, 20px 100%;
          -webkit-mask-size: 100% 100%, 100% 100%, 100% 100%, 100% 100%, 20px 100%, 20px 100%;
          mask-repeat: no-repeat, no-repeat, no-repeat, no-repeat, repeat-x, repeat-x;
          -webkit-mask-repeat: no-repeat, no-repeat, no-repeat, no-repeat, repeat-x, repeat-x;
        }
        @media print {
          .no-print { display: none !important; }
          .ticket-flip-inner { transform: none !important; }
          .ticket-face {
            position: static !important;
            backface-visibility: visible !important;
            transform: none !important;
            box-shadow: none !important;
            page-break-inside: avoid;
            margin-bottom: 24px;
          }
          .print-page-wrap { padding: 0 !important; background: white !important; }
        }
      `}</style>

      <div className="print-page-wrap max-w-md mx-auto">
        <div className="no-print flex items-center justify-between mb-6">
          <button
            onClick={() => router.push("/mypage")}
            className="flex items-center gap-1 text-gray-500 hover:text-gray-700 text-sm font-semibold"
          >
            <ChevronLeft size={18} />
            마이페이지로
          </button>
          <button
            onClick={() => window.print()}
            className="flex items-center gap-1 text-blue-600 hover:text-blue-700 text-sm font-semibold border border-blue-200 hover:border-blue-300 px-3 py-1.5 rounded-lg transition"
          >
            <Printer size={16} />
            인쇄하기
          </button>
        </div>

        <div className="no-print mb-6">
          <h1 className="text-xl font-bold text-gray-800">{group.concertName}</h1>
          <p className="text-sm text-gray-400 mt-1">
            {group.startDate} ~ {group.endDate} · {group.tickets.length}매
          </p>
        </div>

        <div className="max-w-xs mx-auto" style={{ perspective: "1200px" }}>
          <button
            onClick={() => setIsFlipped((prev) => !prev)}
            className="ticket-flip-inner relative w-full block text-left"
            style={{
              transformStyle: "preserve-3d",
              transition: "transform 0.6s cubic-bezier(0.4, 0, 0.2, 1)",
              transform: isFlipped ? "rotateY(180deg)" : "rotateY(0deg)",
            }}
          >
            {/* 앞면: 포스터 */}
            <div
              className="ticket-face ticket-face-mask absolute inset-0 shadow-xl overflow-hidden bg-gray-900"
              style={{ backfaceVisibility: "hidden" }}
            >
              {group.urlPoster ? (
                <img
                  src={getLocalConcertPoster(group.urlPoster)}
                  alt={group.concertName}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-white/60 text-sm">
                  포스터 없음
                </div>
              )}
              <div className="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/80 to-transparent p-5 pt-10 text-center">
                <h3 className="text-white font-bold text-lg leading-snug">{group.concertName}</h3>
                <p className="no-print text-white/70 text-xs mt-2">탭하여 상세정보 보기 ↻</p>
              </div>
            </div>

            {/* 뒷면: 좌석 전체 + 합산 금액 (서비스 시그니처 블루) — 실제 티켓 라벨 구조로 구성 */}
            <div
              className="ticket-face ticket-face-mask relative w-full shadow-xl bg-blue-600 text-white p-6 pt-8 flex flex-col text-left"
              style={{ backfaceVisibility: "hidden", transform: "rotateY(180deg)" }}
            >
              <div className="flex items-center justify-between mb-3">
                <span className="text-[10px] font-semibold tracking-widest text-blue-200">
                  TICKETINGO ORIGINAL TICKET
                </span>
                <span className="bg-white/15 text-xs font-semibold px-2 py-1 rounded-full">
                  {group.round}회차
                </span>
              </div>

              <p className="text-blue-200 text-[10px] tracking-widest mb-1">TITLE</p>
              <h2 className="text-lg font-bold leading-snug mb-3">{group.concertName}</h2>

              <div className="grid grid-cols-2 gap-x-4 text-sm border-t border-white/20 pt-3 mb-3">
                <div>
                  <p className="text-blue-200 text-[10px] tracking-widest mb-0.5">PERIOD</p>
                  <p className="font-semibold">
                    {group.startDate.slice(5)} ~ {group.endDate.slice(5)}
                  </p>
                </div>
                <div>
                  <p className="text-blue-200 text-[10px] tracking-widest mb-0.5">AMOUNT</p>
                  <p className="font-semibold">{group.totalPrice.toLocaleString()}원</p>
                </div>
              </div>

              <div className="border-t border-white/20 pt-3 mb-3">
                <p className="text-blue-200 text-[10px] tracking-widest mb-1.5">
                  SEAT ({group.tickets.length}매)
                </p>
                <div className="space-y-1">
                  {group.tickets.map((t, index) => (
                    <div key={t.ticketId} className="flex items-center justify-between text-sm">
                      <span className="font-semibold">
                        No.{index + 1} &nbsp;{t.gradeName}석 · {t.seatNumber}
                      </span>
                      <span className={t.isValid ? "text-blue-100" : "text-blue-300 line-through"}>
                        {t.ticketPrice.toLocaleString()}원
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="border-t border-white/20 pt-3">
                <p className="text-blue-200 text-[10px] tracking-widest mb-1.5">TICKET NO.</p>
                <div className="space-y-0.5">
                  {group.tickets.map((t, index) => (
                    <p key={t.ticketId} className="text-[11px] text-blue-100 tracking-wide break-all text-left">
                      No.{index + 1} &nbsp;{t.ticketNumber}
                    </p>
                  ))}
                </div>
              </div>

              <div className="mt-3 mb-3">
                <span
                  className={`px-3 py-1 text-xs rounded-full font-semibold ${
                    allInvalid ? "bg-white/20 text-white" : "bg-white text-blue-700"
                  }`}
                >
                  {statusLabel}
                </span>
              </div>

              {group.tickets.some((t) => t.qrToken) && origin && (
                <div className="border-t border-white/20 pt-3 mb-2">
                  <p className="text-blue-200 text-[10px] tracking-widest mb-2">QR CODE</p>
                  <div className="flex gap-3 justify-center flex-wrap">
                    {group.tickets.map((t, index) =>
                      t.qrToken ? (
                        <div key={t.ticketId} className="flex flex-col items-center gap-1">
                          {group.tickets.length > 1 && (
                            <span className="text-[9px] text-blue-200">No.{index + 1}</span>
                          )}
                          <div className="bg-white p-1.5 rounded">
                            <QRCodeSVG
                              value={`${origin}/verify/${t.qrToken}`}
                              size={group.tickets.length === 1 ? 80 : 60}
                            />
                          </div>
                        </div>
                      ) : null
                    )}
                  </div>
                </div>
              )}

              <p className="no-print text-blue-200 text-[11px] mt-auto pt-2 text-center">탭하여 앞면으로 ↻</p>
            </div>
          </button>
        </div>

        <p className="no-print text-xs text-gray-400 text-center mt-6">
          예매 취소는 마이페이지에서만 가능합니다.
        </p>
      </div>
    </div>
  );
}

export default function TicketDetailPage() {
  return (
    <Suspense fallback={<p className="text-center text-gray-400 py-20">불러오는 중...</p>}>
      <TicketDetailContent />
    </Suspense>
  );
}