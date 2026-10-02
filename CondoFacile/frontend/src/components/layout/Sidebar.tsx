'use client';

import Link from 'next/link';
import Image from 'next/image';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
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
  Camera,
  UserCircle,
  Inbox,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { fetchUsersNonAssociati, fetchRichiesteResetPassword, RICHIESTE_AGGIORNATE_EVENT } from '@/lib/api';

const adminNav = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/richieste', label: 'Richieste', icon: Inbox },
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
  { href: '/assemblee', label: 'Assemblee', icon: CalendarDays },
  { href: '/documenti', label: 'Documenti', icon: FileText },
  { href: '/prenotazioni', label: 'Spazi comuni', icon: Home },
];

export default function Sidebar() {
  const pathname = usePathname();
  const { user, token, logout, uploadProfilePhoto } = useAuth();
  const router = useRouter();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [richiesteCount, setRichiesteCount] = useState(0);

  const navItems = user?.role === 'AMMINISTRATORE' ? adminNav : condominoNav;

  useEffect(() => {
    if (!token || user?.role !== 'AMMINISTRATORE') return;
    let cancelled = false;
    const aggiornaConteggio = () => {
      Promise.all([fetchUsersNonAssociati(token), fetchRichiesteResetPassword(token)])
        .then(([registrazioni, resetPassword]) => {
          if (!cancelled) setRichiesteCount(registrazioni.length + resetPassword.length);
        })
        .catch(() => { if (!cancelled) setRichiesteCount(0); });
    };
    aggiornaConteggio();
    // La pagina Richieste segnala ogni approvazione/rifiuto; al rientro sulla scheda si riallinea
    window.addEventListener(RICHIESTE_AGGIORNATE_EVENT, aggiornaConteggio);
    window.addEventListener('focus', aggiornaConteggio);
    return () => {
      cancelled = true;
      window.removeEventListener(RICHIESTE_AGGIORNATE_EVENT, aggiornaConteggio);
      window.removeEventListener('focus', aggiornaConteggio);
    };
  }, [token, user?.role, pathname]);

  const handleLogout = () => {
    logout();
    router.replace('/login');
  };

  const handlePhotoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      uploadProfilePhoto(reader.result as string).catch(console.error);
    };
    reader.readAsDataURL(file);
  };

  const profilePhoto = user?.profilePhoto ?? null;

  return (
    <aside
      className="flex flex-col flex-shrink-0"
      style={{
        width: '240px',
        height: '100vh',
        backgroundColor: '#ffffff',
        borderRight: '1px solid #f0f0f0',
        overflow: 'hidden',
      }}
    >
      {/* Logo */}
      <div className="flex items-center justify-center px-4 py-3 flex-shrink-0" style={{ borderBottom: '1px solid #f5f5f5' }}>
        <Image
          src="/logo.jpeg"
          alt="CondoFacile"
          width={110}
          height={40}
          className="object-contain"
          priority
        />
      </div>

      {/* Profilo utente */}
      {user && (
        <div className="px-4 py-3 flex-shrink-0" style={{ borderBottom: '1px solid #f0f0f0' }}>
          {/* Avatar / Foto */}
          <div className="flex flex-col items-center mb-2">
            <div className="relative">
              {profilePhoto ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={profilePhoto}
                  alt="Foto profilo"
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: '50%',
                    objectFit: 'cover',
                    border: '2px solid var(--primary)',
                  }}
                />
              ) : (
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: '50%',
                    backgroundColor: '#fef2f2',
                    border: '2px solid var(--primary)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <UserCircle size={28} style={{ color: 'var(--primary)' }} />
                </div>
              )}
              <button
                onClick={() => fileInputRef.current?.click()}
                title="Cambia foto profilo"
                style={{
                    position: 'absolute',
                    bottom: 0,
                    right: 0,
                    width: 18,
                    height: 18,
                    borderRadius: '50%',
                    backgroundColor: 'var(--primary)',
                    color: '#fff',
                    border: 'none',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Camera size={10} />
                </button>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handlePhotoChange}
            />
          </div>

          {/* Nome e ruolo */}
          <div className="text-center">
            <p className="text-sm font-bold truncate" style={{ color: '#1a1a1a' }}>
              {user.username}
            </p>
            <p className="text-xs mt-0.5" style={{ color: 'var(--primary)', fontWeight: 600 }}>
              {user.role === 'AMMINISTRATORE' ? '👤 Amministratore' : '🏠 Condomino'}
            </p>
          </div>
        </div>
      )}

      {/* Nav */}
      <nav
        className="flex-1 py-3 px-3 sidebar-nav-scroll"
        style={{ overflowY: 'auto', minHeight: 0 }}
      >
        <p className="text-xs font-semibold uppercase tracking-widest px-3 mb-1.5" style={{ color: '#c0c0c0' }}>
          Menu
        </p>
        <ul className="space-y-0.5">
          {navItems.map(({ href, label, icon: Icon }) => {
            const isActive = pathname === href;
            return (
              <li key={href}>
                <Link
                  href={href}
                  className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-all"
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
                  {href === '/richieste' && richiesteCount > 0 && (
                    <span
                      className="ml-auto text-xs font-bold rounded-full flex items-center justify-center"
                      style={{ backgroundColor: 'var(--primary)', color: '#fff', minWidth: 18, height: 18, padding: '0 5px' }}
                    >
                      {richiesteCount}
                    </span>
                  )}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Footer */}
      <div className="px-4 py-3 flex-shrink-0" style={{ borderTop: '1px solid #f0f0f0' }}>
        <button
          onClick={handleLogout}
          className="flex items-center justify-center gap-2 w-full py-2 rounded-lg text-sm font-semibold transition"
          style={{ backgroundColor: '#fef2f2', color: 'var(--primary)', border: '1px solid #fca5a5' }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLButtonElement).style.backgroundColor = 'var(--primary)';
            (e.currentTarget as HTMLButtonElement).style.color = '#fff';
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#fef2f2';
            (e.currentTarget as HTMLButtonElement).style.color = 'var(--primary)';
          }}
        >
          <LogOut size={15} />
          Logout
        </button>
        <p className="text-xs mt-2" style={{ color: '#bbb' }}>v1.0.0</p>
        <p className="text-xs mt-0.5" style={{ color: '#ccc' }}>© 2026 Roberto Di Flumeri</p>
      </div>
    </aside>
  );
}
