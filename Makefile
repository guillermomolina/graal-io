build:
	mvn -B package -DskipTests

dependency:
	mvn -B dependency:resolve

clean:
	mvn clean

rebuild: 
	mvn clean -B package -DskipTests