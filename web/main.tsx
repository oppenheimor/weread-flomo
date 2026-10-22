import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Auth } from './Auth.tsx';
import { Introduction } from './Introduction.tsx';
import { SharedPage } from './SharedPage.tsx';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>{window.location.pathname.startsWith(`${import.meta.env.BASE_URL}share/`) || window.location.pathname === `${import.meta.env.BASE_URL}share`
    ? <SharedPage /> : window.location.pathname.replace(/\/$/, '') === `${import.meta.env.BASE_URL}introduction`
      ? <Introduction /> : <Auth />}</StrictMode>,
);
