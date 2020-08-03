create table if not exists credential (
  id uuid DEFAULT uuid_generate_v4(),
  email varchar(96) not null,
  password varchar(128) not null,
  PRIMARY KEY (id),
  unique (email)
);


