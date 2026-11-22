import type { Metadata } from "next";
import { ChatRoomView } from "./ChatRoomView";

export const metadata: Metadata = {
  title: "채팅 | 모두의 스터디",
};

export default async function ChatPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ChatRoomView id={id} />;
}
