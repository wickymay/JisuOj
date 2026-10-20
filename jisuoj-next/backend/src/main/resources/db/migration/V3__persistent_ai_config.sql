CREATE TABLE IF NOT EXISTS `jisu_ai_config` (
  `id` tinyint unsigned NOT NULL,
  `base_url` varchar(500) NOT NULL,
  `model` varchar(200) NOT NULL,
  `api_key_ciphertext` text NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体持久化配置；API Key 使用 AES-GCM 加密';
