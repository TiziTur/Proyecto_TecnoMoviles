import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

import authRoutes from './routes/auth';
import userRoutes from './routes/users';
import purchaseRoutes from './routes/purchases';
import productRoutes from './routes/products';
import supermarketRoutes from './routes/supermarkets';
import ticketRoutes from './routes/ticket';
import chatRoutes from './routes/chat';
import priceRoutes from './routes/prices';
import purchaseComparisonRoutes from './routes/purchaseComparison';
import productMatchRoutes from './routes/productMatch';

dotenv.config();

const app = express();
const PORT = process.env.PORT ?? 3000;

app.use(cors());
// Límite default de express.json() es 100kb — muy poco para varias fotos de ticket en base64.
app.use(express.json({ limit: '20mb' }));

// Health check — Railway lo usa para saber si el servicio está vivo
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Rutas
app.use('/auth', authRoutes);
app.use('/users', userRoutes);
app.use('/purchases', purchaseRoutes);
app.use('/purchases/:purchaseId/products', productRoutes);
app.use('/purchases/:purchaseId/scan-ticket', ticketRoutes);
app.use('/supermarkets', supermarketRoutes);
app.use('/chat', chatRoutes);
app.use('/prices', priceRoutes);
app.use('/purchases/:purchaseId/compare', purchaseComparisonRoutes);
app.use('/products', productMatchRoutes);

app.listen(PORT, () => {
  console.log(`SuperAhorro API corriendo en puerto ${PORT}`);
});
