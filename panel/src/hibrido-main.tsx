import { createRoot } from 'react-dom/client';
import { Shell } from './components/Shell';
import { HibridoPage } from './pages/HibridoPage';
import './panel.css';

const root = document.getElementById('root');
if (!root) throw new Error('missing #root');

createRoot(root).render(
  <Shell current="/hibrido.html">
    <HibridoPage />
  </Shell>,
);
