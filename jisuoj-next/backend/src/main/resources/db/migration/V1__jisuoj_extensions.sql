CREATE TABLE IF NOT EXISTS `jisu_user_credential` (
  `user_id` varchar(48) NOT NULL,
  `password_hash` varchar(100) NOT NULL,
  `upgraded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新系统安全凭据；users.password 保留供旧站兼容';

CREATE TABLE IF NOT EXISTS `jisu_problem_source` (
  `problem_id` int NOT NULL,
  `source_type` varchar(24) NOT NULL DEFAULT 'manual',
  `source_ref` varchar(255) NOT NULL DEFAULT '',
  `source_url` varchar(500) NOT NULL DEFAULT '',
  `imported_by` varchar(48) NOT NULL DEFAULT '',
  `imported_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`problem_id`),
  KEY `idx_jisu_problem_source_type` (`source_type`,`source_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目拉取来源审计';

CREATE TABLE IF NOT EXISTS `jisu_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `actor_id` varchar(48) NOT NULL DEFAULT '',
  `action` varchar(64) NOT NULL,
  `target_type` varchar(32) NOT NULL DEFAULT '',
  `target_id` varchar(64) NOT NULL DEFAULT '',
  `detail` json DEFAULT NULL,
  `ip` varchar(46) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_jisu_audit_actor_time` (`actor_id`,`created_at`),
  KEY `idx_jisu_audit_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理操作审计日志';
