import { createRoot } from 'react-dom/client';
import { Shell } from './components/Shell';
import { ModeloPage } from './pages/ModeloPage';
import './panel.css';

const root = document.getElementById('root');
if (!root) throw new Error('missing #root');

createRoot(root).render(
  <Shell current="/modelo.html">
    <ModeloPage />
  </Shell>,
);
