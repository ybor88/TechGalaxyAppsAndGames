'use client';

import Link from 'next/link';
import Image from 'next/image';
import { usePathname, useRouter } from 'next/navigation';
import {
  LayoutDashboard,
  Users,
  CreditCard,
  Wrench,
  Bell,
  CalendarDays,
  FileText,
  Settings,
  BarChart2,
  Truck,
  LogOut,
  Home,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

const adminNav = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/anagrafica', label: 'Anagrafica', icon: Users },
  { href: '/pagamenti', label: 'Quote & Pagamenti', icon: CreditCard },
  { href: '/ticket', label: 'Segnalazioni', icon: Wrench },
  { href: '/comunicazioni', label: 'Comunicazioni', icon: Bell },
  { href: '/assemblee', label: 'Assemblee', icon: CalendarDays },
  { href: '/documenti', label: 'Documenti', icon: FileText },
  { href: '/fornitori', label: 'Fornitori', icon: Truck },
  { href: '/analytics', label: 'Analytics', icon: BarChart2 },
  { href: '/impostazioni', label: 'Impostazioni', icon: Settings },
];

const condominoNav = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/mie-quote', label: 'Le mie Quote', icon: CreditCard },
  { href: '/ticket', label: 'Segnalazioni', icon: Wrench },
  { href: '/comunicazioni', label: 'Bacheca', icon: Bell },
  { href: '/documenti', label: 'Documenti', icon: FileText },
  { href: '/prenotazioni', label: 'Spazi comuni', icon: Home },
];

export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const router = useRouter();

  const navItems = user?.role === 'AMMINISTRATORE' ? adminNav : condominoNav;

  const handleLogout = () => {
    logout();
    router.replace('/login');
  };

  return (
    <aside
      className="flex flex-col flex-shrink-0"
      style={{
        width: '240px',
        minHeight: '100vh',
        backgroundColor: '#ffffff',
        borderRight: '1px solid #f0f0f0',
      }}
    >
      {/* Logo */}
      <div className="flex items-center justify-center px-4 py-5" style={{ borderBottom: '1px solid #f5f5f5' }}>
        <Image
          src="/logo.jpeg"
          alt="CondoFacile"
          width={130}
          height={48}
          className="object-contain"
          priority
        />
      </div>

      {/* Ruolo utente */}
      {user && (
        <div className="px-4 py-3" style={{ borderBottom: '1px solid #f5f5f5', backgroundColor: '#fafafa' }}>
          <p className="text-xs font-semibold" style={{ color: 'var(--primary)' }}>
            {user.role === 'AMMINISTRATORE' ? 'ðŸ‘¤ Amministratore' : 'ðŸ  CondÃ²mino'}
          </p>
          <p className="text-xs truncate" style={{ color: '#888' }}>{user.username}</p>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 py-4 px-3">
        <p className="text-xs font-semibold uppercase tracking-widest px-3 mb-2" style={{ color: '#c0c0c0' }}>
          Menu
        </p>
        <ul className="space-y-0.5">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive = pathname === href;
            return (
              <li key={href}>
                <Link
                  href={href}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all"
                  style={{
                    color: isActive ? 'var(--primary)' : '#555',
                    backgroundColor: isActive ? '#fef2f2' : 'transparent',
                    fontWeight: isActive ? 600 : 400,
                    borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent',
                  }}
                  onMouseEnter={(e) => {
                    if (!isActive) {
                      const el = e.currentTarget as HTMLAnchorElement;
                      el.style.backgroundColor = '#fafafa';
                      el.style.color = '#222';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isActive) {
                      const el = e.currentTarget as HTMLAnchorElement;
                      el.style.backgroundColor = 'transparent';
                      el.style.color = '#555';
                    }
                  }}
                >
                  <Icon size={17} />
                  {label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Footer */}
      <div className="px-4 py-4" style={{ borderTop: '1px solid #f5f5f5' }}>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 text-xs w-full mb-3 px-2 py-1.5 rounded-lg transition"
          style={{ color: '#888', backgroundColor: 'transparent' }}
          onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#fef2f2'; (e.currentTarget as HTMLButtonElement).style.color = 'var(--primary)'; }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = 'transparent'; (e.currentTarget as HTMLButtonElement).style.color = '#888'; }}
        >
          <LogOut size={14} />
          Esci
        </button>
        <p className="text-xs" style={{ color: '#bbb' }}>v1.0.0</p>
        <p className="text-xs mt-0.5" style={{ color: '#ccc' }}>Â© 2026 Roberto Di Flumeri</p>
      </div>
    </aside>
  );
}
