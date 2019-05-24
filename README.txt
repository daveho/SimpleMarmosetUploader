A simple Eclipse plugin to submit projects to the Marmoset
assignment submission and testing server.  It also works
with Pygmy Marmoset (https://github.com/daveho/PygmyMarmoset).

The plugin has an update site:

  https://daveho.github.io/SimpleMarmosetUploader

You can install using Help->Install New Software... in
Eclipse and entering the update site URL.  The update site
has a single feature, so install that, restart, and you
should be good to go.

NOTE: as of version 1.0.7, the plugin does NOT support
self-signed SSL certificates.  Your Marmoset installation needs
to be accessed through an SSL certificate to which a chain
of trust can be established from the keystore included with
the JRE that Eclipse is running in.

More information about Marmoset:

  http://marmoset.cs.umd.edu/

Feedback to dhovemey@ycp.edu
