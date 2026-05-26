import { cookies } from 'next/headers';
import { fetchDashboard } from '@/lib/api';
import Header from '@/components/layout/Header';
import StatsCards from '@/components/dashboard/StatsCards';
import PaymentStatus from '@/components/dashboard/PaymentStatus';
import MonthlyExpenses from '@/components/dashboard/MonthlyExpenses';
import OpenTickets from '@/components/dashboard/OpenTickets';
import UpcomingDeadlines from '@/components/dashboard/UpcomingDeadlines';
import RecentCommunications from '@/components/dashboard/RecentCommunications';
import OngoingWorks from '@/components/dashboard/OngoingWorks';
import EmptyState from '@/components/dashboard/EmptyState';

export default async function DashboardPage() {
  const cookieStore = await cookies();
  const token = cookieStore.get('cf_token')?.value;

  let data;
  try {
    data = await fetchDashboard(token);
  } catch {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div
          className="rounded-xl p-8 text-center"
          style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca' }}
        >
          <p className="font-semibold" style={{ color: '#dc2626' }}>
            Impossibile caricare i dati
          </p>
          <p className="text-sm mt-1" style={{ color: '#6b7280' }}>
            Assicurati che il backend sia avviato su http://localhost:3001
          </p>
        </div>
      </div>
    );
  }

  const isEmpty = !data.nomeCondominio;

  return (
    <div className="flex flex-col flex-1">
      <Header nomeCondominio={data.nomeCondominio || 'CondoFacile'} aggiornato={data.aggiornato} />

      <main className="flex-1 p-6 overflow-auto" style={{ backgroundColor: 'var(--background)' }}>
        {isEmpty ? (
          <EmptyState />
        ) : (
          <>
            {/* Stats row */}
            <StatsCards data={data} />

            {/* Charts row */}
            <div className="grid grid-cols-2 gap-4 mt-4">
              <PaymentStatus data={data.statoPagementi} />
              <MonthlyExpenses data={data.speseUltimiMesi} />
            </div>

            {/* Detail grids */}
            <div className="grid grid-cols-2 gap-4 mt-4">
              <OpenTickets tickets={data.segnalazioniAperte} />
              <UpcomingDeadlines deadlines={data.scadenzeImminenti} />
            </div>

            <div className="grid grid-cols-2 gap-4 mt-4">
              <RecentCommunications communications={data.comunicazioniRecenti} />
              <OngoingWorks works={data.lavoriInCorso} />
            </div>
          </>
        )}
      </main>
    </div>
  );
}

