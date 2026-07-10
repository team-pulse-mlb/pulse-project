import { useState } from 'react';
import { useNavigate } from 'react-router';

import Card from '../../../shared/components/Card';
import { notificationFixtures } from '../fixtures';

function NotificationsPage() {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState(notificationFixtures);
  const markAllRead = () => setNotifications((current) => current.map((item) => ({ ...item, unread: false })));
  const openGame = (notificationId: number, gameId: number) => {
    setNotifications((current) => current.map((item) => item.id === notificationId ? { ...item, unread: false } : item));
    navigate(`/games/${gameId}`, { state: { fromCard: true } });
  };

  return (
    <div className="mx-auto max-w-[560px] px-4 py-8">
      <div className="mb-6 flex items-center justify-between gap-4">
        <h1 className="font-display text-2xl font-bold text-text-strong">알림</h1>
        <button type="button" onClick={markAllRead} className="text-sm font-semibold text-text-faint hover:text-text-strong">모두 읽음</button>
      </div>
      <Card flush>
        <ul className="divide-y divide-divider overflow-hidden rounded-card">
          {notifications.map((notification) => (
            <li key={notification.id}>
              <button type="button" onClick={() => openGame(notification.id, notification.gameId)} className={`flex w-full items-center gap-4 px-5 py-5 text-left hover:bg-page ${notification.unread ? 'bg-red-tint-soft' : 'bg-white'}`}>
                <span aria-hidden="true" className={`h-2.5 w-2.5 shrink-0 rounded-full ${notification.unread ? 'bg-mlb-red' : 'border border-card-border bg-white'}`} />
                <span className="min-w-0 flex-1">
                  <span className={`block text-[15px] text-text-strong ${notification.unread ? 'font-bold' : 'font-medium'}`}>{notification.message}</span>
                  <span className="mt-2 block text-sm text-text-faint"><strong className="font-display text-text-muted">{notification.matchup}</strong><span className="mx-2">·</span>{notification.relativeTime}</span>
                </span>
                <span aria-hidden="true" className="text-text-faint">→</span>
              </button>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

export default NotificationsPage;
