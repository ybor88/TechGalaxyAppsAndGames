'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function AnalyticsRedirect() {
  const router = useRouter();
  useEffect(() => { router.push('/fornitori/analytics'); }, [router]);
  return <div style={{ padding: 24 }}>Reindirizzamento a Fornitori Analytics...</div>;
}
