import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Auth } from './Auth.tsx';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode><Auth /></StrictMode>,
);
