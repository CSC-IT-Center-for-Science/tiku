SET DATABASE SQL SYNTAX PGS true;
CREATE SCHEMA amor_test;


CREATE TABLE amor_test.meta_hydra (
	run_id bigint primary key,
	subject text not null,
	hydra text not null,
	added_meta_hydra timestamp default now()
);

CREATE TABLE amor_test.meta_table (
	run_id bigint primary key references amor_test.meta_hydra,
	logical_name text
);

CREATE TABLE amor_test.meta_state (
	run_id bigint primary key references amor_test.meta_hydra,
	state text
);

-- NOTE: This should be a view in production enviroment 
--       and generated from summary xml definitions
CREATE TABLE amor_test.amor_summaries (
	run_id bigint,
	subject text,
	hydra text,
	summary_id text,
	title text,
	lang text,
	subject_title text,
	subject_lang text,
	constraint pk_summary primary key (run_id, subject, hydra, summary_id)
);

	
CREATE TABLE amor_test.meta_ref_id_map (
	ref text primary key,
	map_id bigint not null
);


-- 
--
-- TEST TABLES START HERE
--
-- 

CREATE TABLE amor_test.x1000_fact (
	time_key bigint,
	area_key bigint,
	measure_key bigint,
	val text,
	constraint pk_fact primary key (time_key, area_key, measure_key) 
);

CREATE TABLE amor_test.x1000_tree (
	key bigint primary key,
	parent_key bigint references amor_test.x1000_tree,
	dim text not null,
	stage text not null,
	ref text not null
);

CREATE TABLE amor_test.x1000_meta (
	ref text,
	lang text,
	tag text,
	data text,
	constraint pk_meta primary key (ref, tag, lang)
);


CREATE TABLE amor_test.x1003_meta (
	ref text,
	tag text,
	lang text,
	data text,
);

CREATE TABLE amor_test.x1003_tree (
	key text,
	parent_key text,
	dim text,
	stage text,
	ref text
);

CREATE TABLE amor_test.x1003_fact (
	time_key text,
	region_key text,
	measure_key text,
	val text
);

-- -------------
--
-- TEST DATA STARTS HERE
--
-- -------------


INSERT INTO amor_test.meta_hydra(
	run_id,
	subject,
	hydra,
	added_meta_hydra
) values (
	1000,
	'x-subject',
	'test-hydra',
	to_date('2016-09-05','yyyy-mm-dd')
), (
	1001,
	'a-subject',
	'test-hydra',
	to_date('2016-09-03','yyyy-mm-dd')
), (
	1003,
	'a-subject',
	'test-hydra',
	to_date('2016-09-04','yyyy-mm-dd')
),
(
	1002,
	'third-subject',
	'test-hydra',
	to_date('2016-09-02','yyyy-mm-dd')
);

INSERT INTO amor_test.meta_state 
	(run_id, state) 
VALUES 
	(1000, 'test'),
	(1001, 'test'),
	(1003, 'test'),
	(1002, 'prod');
	
INSERT INTO amor_test.meta_table 
	(run_id, logical_name)
VALUES 
	(1000, 'fact'),
	(1001, 'fact'),
	(1003, 'fact'),
	(1002, 'fact');

INSERT INTO amor_test.x1003_meta 
	(ref, tag, lang, data)
VALUES
	('https://sampo.thl.fi/pivot/prod/fi/a-subject/test-hydra/fact', 'is', null, 'fact'),
	('https://sampo.thl.fi/pivot/prod/fi/a-subject/test-hydra/fact', 'name', 'fi', 'Test fact'),
	('https://sampo.thl.fi/pivot/prod/fi/a-subject/test-hydra/fact', 'opendata', null, '1'),
	('https://sampo.thl.fi/pivot/prod/fi/a-subject/test-hydra/fact', 'deny', null, '1'),
	('https://sampo.thl.fi/pivot/prod/fi/a-subject/test-hydra/fact', 'password', null, 'Test password');
	
	
INSERT INTO amor_test.x1003_tree
	(key, parent_key, dim, stage, ref)
VALUES
	('1', null, 'region', 'root', 'https://sampo.thl.fi/meta/alue/kokomaa'),
	('2', '1', 'region', 'leaf', 'https://sampo.thl.fi/meta/alue/kunta/2016/091'),
	('3', '1', 'region', 'leaf', 'https://sampo.thl.fi/meta/alue/kunta/2016/092'),
	('4', '1', 'region', 'leaf', 'https://sampo.thl.fi/meta/alue/kunta/2016/049'),
	('5', null, 'time', 'root', 'https://sampo.thl.fi/meta/aika/'),
	('6', '5', 'time', 'leaf', 'https://sampo.thl.fi/meta/aika/vuosi/2016'),
	('7', null, 'measure','root', 'https://sampo.thl.fi/meta/test/mittari'),
	('8', '7', 'measure','leaf', 'https://sampo.thl.fi/meta/test/mittari/2');
	
INSERT INTO amor_test.meta_ref_id_map 
	(map_id, ref)
VALUES
	(1, 'https://sampo.thl.fi/meta/alue/kokomaa'),
	(2, 'https://sampo.thl.fi/meta/alue/kunta/2016/091'),
	(3, 'https://sampo.thl.fi/meta/alue/kunta/2016/092'),
	(4, 'https://sampo.thl.fi/meta/alue/kunta/2016/049'),
	(5, 'https://sampo.thl.fi/meta/aika/'),
	(6, 'https://sampo.thl.fi/meta/aika/vuosi/2016'),
	(7, 'https://sampo.thl.fi/meta/test/mittari'),
	(8, 'https://sampo.thl.fi/meta/test/mittari/2');
	
INSERT INTO amor_test.x1003_meta 
	(ref, tag, lang, data)
VALUES 
	('https://sampo.thl.fi/meta/alue/kokomaa', 'name', 'fi', 'Koko maa'),
	('https://sampo.thl.fi/meta/alue/kunta/2016/091', 'name', 'fi', 'Helsinki'),
	('https://sampo.thl.fi/meta/alue/kunta/2016/092', 'name', 'fi', 'Vantaa'),
	('https://sampo.thl.fi/meta/alue/kunta/2016/049', 'name', 'fi', 'Espoo'),
	('https://sampo.thl.fi/meta/aika/', 'name', 'fi', 'Kaikki vuodet'),
	('https://sampo.thl.fi/meta/aika/vuosi/2016', 'name', 'fi', '2016'),
	('https://sampo.thl.fi/meta/test/mittari', 'name', 'fi', 'Mittari'),
	('https://sampo.thl.fi/meta/alue/kunta/2016/091', 'sort', 'fi', '2'),
	('https://sampo.thl.fi/meta/alue/kunta/2016/092', 'sort', 'fi', '3'),
	('https://sampo.thl.fi/meta/alue/kunta/2016/049', 'sort', 'fi', '1'),
	('https://sampo.thl.fi/meta/test/mittari', 'decimals', null, '2'),
	('https://sampo.thl.fi/meta/test/mittari/2', 'decimals', null, 'kaksi');
	
	

INSERT INTO  amor_test.amor_summaries 
	(run_id, subject, hydra, summary_id, title, subject_title)
VALUES
	(1003, 'a-subject', 'test-hydra', 'summary-1', 'Test summary', 'Test subject'),
	(1003, 'a-subject', 'test-hydra', 'summary-2', 'Second summary', 'Second subject');
		