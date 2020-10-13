create table if not exists auth_token (
  id uuid DEFAULT uuid_generate_v4(),
  credential_id uuid,
  token_user_name uuid DEFAULT uuid_generate_v4(),
  token_secret uuid DEFAULT uuid_generate_v4(),
  valid_until timestamp not null,
  is_logged_out boolean not null,
  PRIMARY KEY (id),
  FOREIGN KEY (credential_id) references credential (id)
);

