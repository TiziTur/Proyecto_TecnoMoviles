import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

import authRoutes from './routes/auth';
import userRoutes from './routes/users';
import purchaseRoutes from './routes/purchases';
import productRoutes from './routes/products';
import supermarketRoutes from './routes/supermarkets';

dotenv.config();

const app = express();
const PORT = process.env.PORT ?? 3000;

app.use(cors());
app.use(express.json());

// Health check — Railway lo usa para saber si el servicio está vivo
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Rutas
app.use('/auth', authRoutes);
app.use('/users', userRoutes);
app.use('/purchases', purchaseRoutes);
app.use('/purchases/:purchaseId/products', productRoutes);
app.use('/supermarkets', supermarketRoutes);

app.listen(PORT, () => {
  console.log(`SuperAhorro API corriendo en puerto ${PORT}`);
});
