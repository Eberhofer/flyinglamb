CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table if not exists camt_file (
  id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
  file_name varchar(96) not null,
  statement_id varchar(24) not null,
  message_id varchar(24) not null,
  electronic_sequence_number int not null,
  creation_time timestamp not null,
  iban char(21) not null,
  currency char(3) not null,
  from_date timestamp not null,
  to_date timestamp not null,
  open_balance numeric(10,2) not null,
  close_balance numeric(10,2) not null,
  UNIQUE (statement_id)
);

create table if not exists camt_file_content (
  camt_file_id uuid PRIMARY KEY,
  camt_file_content text not null,
  FOREIGN KEY (camt_file_id) references camt_file (id)
);

create table if not exists camt_transaction (
  id uuid DEFAULT uuid_generate_v4(),
  camt_file_id uuid not null,
  iban char(21) not null,
  booking_date timestamp not null,
  value_date timestamp not null,
  is_reversal boolean not null,
  currency char(3) not null,
  amount numeric(12,2) not null,
  additional_info text not null,
  account_servicer_reference varchar(55) not null,
  transaction_references text not null,
  bank_transaction_code text not null,
  PRIMARY KEY (id),
  foreign key (camt_file_id) references camt_file (id)
);

