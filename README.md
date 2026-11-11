<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHVoidItems

*Garde les items tombés dans le vide d'une île, rachetables auprès du Marchand des abîmes.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Garde les items tombés dans le vide d'une île, rachetables auprès du Marchand des abîmes.

Un item qui sort du monde par le bas, à moins de `void-margin` blocs d'une île, est retenu pour cette île : les items identiques se
cumulent et l'ensemble disparaît `defaults.expire-seconds` après le premier. Les membres de l'île les rachètent dans un menu de 54
emplacements (`/void`) contre des éclats d'écho, ou de l'argent Vault selon `currency.type`. Le prix se règle par matériau
(`custom.<MATÉRIAU>.price`) et `blacklist` écarte des matériaux.

Chaque rachat est écrit dans le fichier `rachats` de SHLogs (`plugins/SHLogs/SHVoidItems/`).

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `Vault`
- Optionnelles :
  - `SHLogs` : journal des rachats, sinon écrits dans la console ;
  - `WildStacker` : la quantité retenue pour une pile d'items au sol est sa quantité réelle, et non celle, plafonnée, de l'entité ;
  - `SHInventoryPages` : un rachat se range dans l'inventaire porté puis dans les autres pages du joueur, et ne demande plus d'emplacements libres dans l'inventaire porté ;
  - `SHNetwork` et `SHSkyBridge` : mode réseau

## Mode réseau

Avec **SHSkyBridge** et **SHNetwork** installés (plusieurs serveurs se partagent les îles, chacune étant hébergée par un seul) et `reseau.actif: true`
(valeur par défaut) :

- chaque serveur ne garde dans son `data.yml` que les items tombés dans le vide des îles qu'il héberge ; les entrées des autres îles ne sont ni modifiées ni expirées ici ;
- les entrées d'une île suivent l'île quand SHSkyBridge la déplace (`/skybridge deplacer`) ;
- `/void` envoie le joueur sur le serveur de son île quand elle est ailleurs : le menu s'ouvre à son arrivée (durée de validité `reseau.arrivee-secondes`) ;
- `/void <joueur>` indique le serveur de l'île quand elle n'est pas hébergée ici.

Sans eux, ou sans la clé `reseau`, le plugin se comporte comme avant.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/void` | `/shvoid` | Ouvre le menu des Void Items de votre île | `shvoiditems.use` |
| `/shvoiditems view <joueur>` | `/void <joueur>` | Ouvre le menu des Void Items de l'île d'un joueur en ligne (complétion des noms) | `shvoiditems.admin.view` |
| `/shvoiditems reload` | — | Relit `config.yml` et `messages.yml` (un fichier illisible ne change rien, `data.yml` n'est jamais relu) | `shvoiditems.admin.reload` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shvoiditems.use` | Permet d’ouvrir son propre menu void. | `true` |
| `shvoiditems.admin` | Toutes les sous-commandes de `/shvoiditems`. | `op` |
| `shvoiditems.admin.view` | Permet d’ouvrir le menu void d’un autre joueur. | `op` |
| `shvoiditems.admin.reload` | Recharger la configuration. | `op` |

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
