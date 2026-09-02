import { createRoot } from 'react-dom/client';
import { Shell } from './components/Shell';
import { OutboxPage } from './pages/OutboxPage';
import './panel.css';

const root = document.getElementById('root');
if (!root) throw new Error('missing #root');

createRoot(root).render(
  <Shell current="/outbox.html">
    <OutboxPage />
  </Shell>,
);
