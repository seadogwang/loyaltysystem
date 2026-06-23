import React from 'react';
import { HelmetProvider } from 'react-helmet-async';
import { EditorPage } from '../chartdb/pages/editor-page/editor-page';
import { TooltipProvider } from '../chartdb/components/tooltip/tooltip';
import '../chartdb/index.css';
import '../chartdb/globals.css';
import '../chartdb/i18n/i18n';
import '../chartdb/polyfills';

const ChartDBPage: React.FC = () => {
  return (
    <HelmetProvider>
      <TooltipProvider>
        <EditorPage />
      </TooltipProvider>
    </HelmetProvider>
  );
};

export default ChartDBPage;