-- Schema de base de datos para SuperAhorro
-- Ejecutar este script en la consola de PostgreSQL de Railway

-- Tabla de usuarios
CREATE TABLE IF NOT EXISTS users (
    id            SERIAL PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(50)  DEFAULT '',
    created_at    TIMESTAMP    DEFAULT NOW()
);

-- Tabla de compras
CREATE TABLE IF NOT EXISTS purchases (
    id               SERIAL PRIMARY KEY,
    user_id          INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purchase_date    DATE         NOT NULL,
    purchase_time    TIME         NOT NULL DEFAULT '00:00',
    supermarket      VARCHAR(100) NOT NULL,
    total            NUMERIC(10,2) NOT NULL DEFAULT 0,
    ticket_image_uri TEXT,
    created_at       TIMESTAMP    DEFAULT NOW()
);

-- Tabla de productos
CREATE TABLE IF NOT EXISTS products (
    id          SERIAL PRIMARY KEY,
    purchase_id INTEGER       NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    code        VARCHAR(100)  DEFAULT '',
    name        VARCHAR(255)  NOT NULL,
    description TEXT          DEFAULT '',
    price       NUMERIC(10,2) NOT NULL,
    quantity    INTEGER       NOT NULL DEFAULT 1,
    created_at  TIMESTAMP     DEFAULT NOW()
);

-- Índices para mejorar performance de las queries más frecuentes
CREATE INDEX IF NOT EXISTS idx_purchases_user_id ON purchases(user_id);
CREATE INDEX IF NOT EXISTS idx_purchases_date    ON purchases(purchase_date DESC);
CREATE INDEX IF NOT EXISTS idx_products_purchase ON products(purchase_id);

-- Precios de referencia cargados desde SEPA (datos.produccion.gob.ar)
CREATE TABLE IF NOT EXISTS reference_prices (
  id           SERIAL PRIMARY KEY,
  product_name TEXT NOT NULL,
  brand        TEXT DEFAULT '',
  supermarket  TEXT NOT NULL,
  price        NUMERIC(10,2) NOT NULL,
  province     TEXT DEFAULT '',
  updated_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ref_prices_name ON reference_prices (LOWER(product_name));
