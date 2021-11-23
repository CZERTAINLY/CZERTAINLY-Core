#!/bin/sh

awk '
   split_after == 1 {n++;split_after=0}
   /-----END CERTIFICATE-----/ {split_after=1}
   {print > "cert" n+1 ".pem"}' < $1

for f in cert*.pem
do
  keytool -import -trustcacerts -keystore trustStore.jks -file $f -alias $f -noprompt -storepass $TS_PASSWORD
done

rm cert*.pem