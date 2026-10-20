import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Auth } from './Auth.tsx';
import { Introduction } from './Introduction.tsx';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>{window.location.pathname.replace(/\/$/, '') === `${import.meta.env.BASE_URL}introduction`
    ? <Introduction /> : <Auth />}</StrictMode>,
);
