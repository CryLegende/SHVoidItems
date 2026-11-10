<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHVoidItems

*Garde les items tombés dans le vide d'une île, rachetables auprès du Marchand des abîmes.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Garde les items tombés dans le vide d'une île, rachetables auprès du Marchand des abîmes.

Chaque rachat est écrit dans le fichier `rachats` de SHLogs (`plugins/SHLogs/SHVoidItems/`).

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `Vault`
- Optionnelles : `SHLogs` (journal des rachats, sinon écrits dans la console), `SHNetwork` et `SHSkyBridge` (mode réseau)

## Mode réseau

Avec **SHSkyBridge** et **SHNetwork** installés (plusieurs serveurs se partagent les îles, chacune étant hébergée par un seul) et `reseau.actif: true`
(valeur par défaut) :

- chaque serveur ne garde dans son `data.yml` que les items tombés dans le vide des îles qu'il héberge ; les entrées des autres îles ne sont ni modifiées ni expirées ici ;
- les entrées d'une île suivent l'île quand SHSkyBridge la déplace (`/skybridge deplacer`) ;
- `/shvoid` envoie le joueur sur le serveur de son île quand elle est ailleurs : le menu s'ouvre à son arrivée (durée de validité `reseau.arrivee-secondes`) ;
- `/shvoid <joueur>` indique le serveur de l'île quand elle n'est pas hébergée ici.

Sans eux, ou sans la clé `reseau`, le plugin se comporte comme avant.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shvoid` | `/void` | Ouvre le menu des Void Items pour vous ou pour un autre joueur (admin) | `shvoiditems.use` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shvoiditems.use` | Permet d’ouvrir son propre menu void. | `true` |
| `shvoiditems.admin` | Permet d’ouvrir le menu void d’un autre joueur. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHVoidItems/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
