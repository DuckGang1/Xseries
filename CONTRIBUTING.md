I'm really thankful for all the bug fixes and performance improvements.\
Even if you changed a single line that has a good impact on the performance, it's welcomed. 

Some changes may need a discussion about quality and usage.
Make sure to explain your changes clearly when creating a pull request.
All the pull requests are merged directly into the master branch.

This project imports the full Spigot JAR from an unofficial repo as SkullUtils uses `com.mojang.authlib`
It's also used for JavaX nullability annotations.

Do not make any PRs/issues regarding adding support for new Minecraft versions. I'll be usually finishing the update within the first week of Spigot release. 
Having multiple developers work on the same issue will be just a waste of time and resources.
You should also not make any PRs before support is added when a new Minecraft version comes out even 
if it's unrelated to adding support for that version since your changes are likely to conflict with the update.

### Rules
* Only Java 8 should be used. All the functions in the latest version of Java 8 can be used.
* Make sure the utility works on different Minecraft server versions.
* Use method and variable names that make sense and are related to the context.
* Don't use Optional everywhere that can return null.
* Using Google's Guava is a plus, but not always. Make sure what you're using is supported in
older versions of Bukkit's libraries and don't. Don't use other libraries included in Bukkit, specially Apache Commons since it was removed.
* Add JavaDocs with proper formatting. It's also preferred to explain how the complex parts of a method work
inside the method. Use simple English when possible.
* All the functions used in the utilities should be compatible with Bukkit, Spigot and Paper.
Using extra methods from Spigot is a plus as long as it supports Bukkit, but do not use any methods that are included in any forks of Spigot.
* Change the class version properly. If you're not sure how versioning works, don't change it.
* Each utility should be independent except the ones that are not intended.
Functions such as the common ISFLAT boolean check should not depend on XMaterial's isNewVersion() except
XBlock which is intended, since it already uses XMaterial for materials. Same for XParticle and ParticleDisplay.
* Do not attempt to support versions older than 1.8 even if it can be fixed with a single line.
* Do not use one liner if statements if it doesn't fit the screen.
* Try to avoid streams. Mostly for frequently used methods.