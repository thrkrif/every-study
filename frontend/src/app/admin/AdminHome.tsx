"use client";

import Link from "next/link";
import { AdminGate } from "@/components/AdminGate";
import styles from "./appeals/AdminAppealsView.module.css";

/** 운영자 첫 화면 (PRD 5장 화면 13): 처리할 목록으로 가는 길 */
export function AdminHome() {
  return (
    <AdminGate>
      <main className={styles.page}>
        <h1 className={styles.title}>운영</h1>
        <ul className={styles.list}>
          <li className={styles.card}>
            <Link href="/admin/appeals">이의 큐와 출석 정정</Link>
          </li>
          <li className={styles.card}>
            <Link href="/admin/reports">신고 큐와 제재 관리</Link>
          </li>
        </ul>
      </main>
    </AdminGate>
  );
}
