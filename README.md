# flyinglamb
## Goals
flyinglamb is a first steps project towards a personal finance accounting system using scala akka actor and postgres wiht flyway. It's created for my personal needs and thus highly idiosyncratic and uses technologies I'm interested in - mainly scala, actors and streams. In the first step, the main goal is to put an essential set of services in place without worrying too much about code quality and testing to achieve "Durchstich". Once this is in place, I plan to add tests and start refactoring as well as adding services and features. 
## Flyway
The current implementation uses flyway-sbt and migrations are triggered manually by using 'sbt flywayMigrate'.



