import { createRoot } from 'react-dom/client';
import { Shell } from './components/Shell';
import { LecturaPage } from './pages/LecturaPage';
import './panel.css';

const root = document.getElementById('root');
if (!root) throw new Error('missing #root');

createRoot(root).render(
  <Shell current="/lectura.html">
    <LecturaPage />
  </Shell>,
);
