import { createRoot } from 'react-dom/client';
import { Shell } from './components/Shell';
import { FanoutPage } from './pages/FanoutPage';
import './panel.css';

const root = document.getElementById('root');
if (!root) throw new Error('missing #root');

createRoot(root).render(
  <Shell current="/fanout.html">
    <FanoutPage />
  </Shell>,
);
