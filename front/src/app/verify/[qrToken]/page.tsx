"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

interface TicketVerifyResponse {
  concertName: string;
  venueName: string;
  scheduleDate: string;
  seatNumber: string;
  isValid: boolean;
}

type VerifyState =
  | { status: "loading" }
  | { status: "valid"; data: TicketVerifyResponse }
  | { status: "invalid" }
  | { status: "error" };

export default function VerifyPage() {
  const params = useParams();
  const qrToken = params.qrToken as string;
  const [state, setState] = useState<VerifyState>({ status: "loading" });

  useEffect(() => {
    if (!qrToken) {
      setState({ status: "invalid" });
      return;
    }

    fetch(`${BASE_URL}/api/v1/tickets/verify/${qrToken}`)
      .then(async (res) => {
        if (res.status === 404) {
          setState({ status: "invalid" });
          return;
        }
        if (!res.ok) {
          setState({ status: "error" });
          return;
        }
        const json = await res.json();
        if (!json.data?.isValid) {
          setState({ status: "invalid" });
          return;
        }
        setState({ status: "valid", data: json.data });
      })
      .catch(() => setState({ status: "error" }));
  }, [qrToken]);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-6">
      <div className="bg-white rounded-2xl shadow-sm p-8 max-w-sm w-full text-center">
        {state.status === "loading" && (
          <p className="text-gray-400 text-sm">검증 중...</p>
        )}

        {state.status === "valid" && (
          <>
            <div className="text-5xl mb-4">✅</div>
            <h1 className="text-xl font-bold text-gray-800 mb-1">유효한 티켓입니다</h1>
            <p className={`text-xs font-semibold px-3 py-1 rounded-full inline-block mb-6 ${
              state.data.isValid
                ? "bg-green-100 text-green-700"
                : "bg-gray-100 text-gray-400"
            }`}>
              {state.data.isValid ? "예매완료" : "취소됨"}
            </p>
            <div className="space-y-3 text-sm text-left border-t border-gray-100 pt-5">
              <div className="flex justify-between">
                <span className="text-gray-400 w-20 shrink-0">콘서트</span>
                <span className="text-gray-800 font-semibold text-right">{state.data.concertName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400 w-20 shrink-0">공연장</span>
                <span className="text-gray-800 font-semibold text-right">{state.data.venueName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400 w-20 shrink-0">일시</span>
                <span className="text-gray-800 font-semibold text-right">
                  {new Date(state.data.scheduleDate).toLocaleString("ko-KR", {
                    year: "numeric",
                    month: "long",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400 w-20 shrink-0">좌석</span>
                <span className="text-gray-800 font-semibold text-right">{state.data.seatNumber}</span>
              </div>
            </div>
          </>
        )}

        {state.status === "invalid" && (
          <>
            <div className="text-5xl mb-4">❌</div>
            <h1 className="text-xl font-bold text-gray-800 mb-2">유효하지 않은 티켓입니다</h1>
            <p className="text-sm text-gray-400">QR 코드를 다시 확인해주세요.</p>
          </>
        )}

        {state.status === "error" && (
          <>
            <div className="text-5xl mb-4">⚠️</div>
            <h1 className="text-xl font-bold text-gray-800 mb-2">오류가 발생했습니다</h1>
            <p className="text-sm text-gray-400">잠시 후 다시 시도해주세요.</p>
          </>
        )}
      </div>
    </div>
  );
}
