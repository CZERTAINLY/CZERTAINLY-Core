mvn install:install-file \
   -Dfile=./lib/csr-validation.jar \
   -DgroupId=com.microsoft.intune.scep \
   -DartifactId=csr-validation \
   -Dversion=0.0.1-SNAPSHOT \
   -Dpackaging=jar \
   -DgeneratePom=true