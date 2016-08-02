# lider-ahenk-example-registration

Lider Ahenk example registration bundle

## How to Build

Just run `mvn clean install -DskipTests`

## How to Run

> Make sure you have Lider running. See these documentation:  [\[1\]](https://github.com/Pardus-Kurumsal/lider/wiki/02.-Building-&-Running)

1. Type `bundle:install mvn:tr.org.liderahenk/example-registration/1.0.0-SNAPSHOT` on Karaf shell. This will add bundle to the Karaf instance.
2. Again on Karaf shell, run `bundle:start <BUNDLE_ID>` to run plugin bundle.
3. Finally, restart XMPP Client bundle via `bundle:restart <BUNDLE_ID>`.

