# hello-liberty-tekton

Klein oefenproject: een Jakarta EE REST-app op **Open Liberty**, gebouwd met een **Tekton**-pipeline
(OpenShift Pipelines) en uitgerold op **OpenShift**.

```
GitHub ──► git-clone ──► maven-build ──► buildah ──► deploy ──► Route (https://…/api/hello)
            (Red Hat)     (eigen Task)    (Red Hat)   (eigen Task)
                     └──────── één gedeelde workspace (PVC) ────────┘
```

## Wat zit erin

| Bestand | Wat |
|---|---|
| `pom.xml`, `src/main/java/demo/*` | REST-endpoint `GET /api/hello` (Jakarta EE 10) + JUnit-test |
| `src/main/liberty/config/server.xml` | Liberty-config: alleen `restfulWS` en `mpHealth` |
| `Dockerfile` | Liberty-image op UBI, installeert alleen de benodigde features |
| `k8s/app.yaml` | Deployment + Service + Route (met readiness/liveness op `/health`) |
| `tekton/tasks.yaml` | Eigen Tasks: `maven-build` en `deploy` |
| `tekton/pipeline.yaml` | De Pipeline `hello-ci` |
| `tekton/pipelinerun.yaml` | Eén run starten |
| `tekton/triggers.yaml` | Optioneel: automatisch starten bij `git push` |

## Stappen

### 1. Tools
Download `oc` en `tkn` uit je OpenShift-console: **?-icoon rechtsboven → Command Line Tools**.
Zet ze in een map op je `PATH`. `tkn` is handig maar niet verplicht; alles kan ook met `oc`.

### 2. Inloggen en controleren
Console → je naam rechtsboven → **Copy login command** → plak in je terminal:
```bash
oc login --token=<token> --server=<api-url>
oc project                       # in welke namespace zit je?
oc get task -n openshift-pipelines git-clone buildah   # is OpenShift Pipelines aanwezig?
```
Krijg je bij de laatste regel een fout, zie *Problemen* onderaan.

### 3. Code in een eigen Git-repo
Maak een **publieke** repo `hello-liberty-tekton` op GitHub en push dit project:
```bash
git init -b main
git add .
git commit -m "Liberty + Tekton oefenproject"
git remote add origin https://github.com/<jij>/hello-liberty-tekton.git
git push -u origin main
```
Pas daarna in `tekton/pipelinerun.yaml` de `git-url` aan naar jouw repo.

### 4. Pipeline in het cluster zetten
```bash
oc apply -f tekton/tasks.yaml
oc apply -f tekton/pipeline.yaml
```
Bekijk in de console: **Pipelines → Pipelines → hello-ci**: je ziet de vier Tasks als grafiek.

### 5. Draaien
```bash
oc create -f tekton/pipelinerun.yaml
tkn pipelinerun logs -f --last    # of volg hem in de console: Pipelines → PipelineRuns
```
De eerste run duurt een paar minuten (Maven-downloads, Liberty-features). Aan het eind print
de `deploy`-task de URL. Testen:
```bash
curl https://$(oc get route hello-liberty -o jsonpath='{.spec.host}')/api/hello
```

### 6. (Optioneel) Automatisch starten bij een push
```bash
oc create secret generic github-webhook-secret --from-literal=secretToken=<zelfbedacht-geheim>
oc apply -f tekton/triggers.yaml
oc create route edge el-hello-listener --service=el-hello-listener
oc get route el-hello-listener -o jsonpath='{.spec.host}'
```
GitHub → repo → **Settings → Webhooks → Add webhook**:
- Payload URL: `https://<host-van-hierboven>`
- Content type: `application/json`
- Secret: je zelfbedachte geheim
- Event: *Just the push event*

Wijzig daarna de tekst in `HelloResource.java`, push, en zie een nieuwe PipelineRun starten.

## Dingen om mee te oefenen (en te kunnen vertellen)
1. **Laat de test falen** (pas `MESSAGE` aan in de test). De pipeline stopt na `build`; er komt geen image.
2. **Bekijk de pods** van een run: `oc get pods`. Elke Task is een eigen pod, elke step een container.
3. **Bekijk het image**: `oc get imagestream hello-liberty`. De tag is de Git-commit,
   de deploy gebruikt de digest.
4. **Schaal op**: `oc scale deploy/hello-liberty --replicas=2` en roep `/api/hello` een paar
   keer aan; de podnaam in het antwoord wisselt.
5. **Breid uit**: een SonarQube/SonarCloud-task tussen `build` en `image`.

## Hoe dit zich verhoudt tot de Belastingdienst-opzet
- **Registry**: hier de interne OpenShift-registry; daar **Harbor**.
- **Deploy**: hier `oc apply` vanuit Tekton; daar werkt Tekton de image-tag bij in een GitOps-repo
  en rolt **ArgoCD** uit naar ont/tst/acc/prd.
- **Git**: hier GitHub; daar **Bitbucket** (de webhook en TriggerBinding verschillen dan iets).
- **App**: echte apps hebben een DB2-datasource in `server.xml`, met wachtwoorden uit een Secret.

## Problemen
| Symptoom | Oorzaak / oplossing |
|---|---|
| `tasks.tekton.dev "git-clone" not found` in `openshift-pipelines` | Oudere OpenShift Pipelines (< 1.17) gebruikt ClusterTasks. Check `oc get clustertask`. Vervang dan in `pipeline.yaml` het `resolver: cluster`-blok door `kind: ClusterTask` + `name: git-clone` (idem buildah), en gebruik kleine letters voor de parameters (`url`, `revision`). Bij de result-naam ook `commit` i.p.v. `COMMIT`. |
| Geen enkele Task/Pipeline bekend | OpenShift Pipelines-operator niet geïnstalleerd. Op de Developer Sandbox staat hij er standaard op; op een eigen cluster via OperatorHub (cluster-admin nodig). |
| Parameter onbekend bij git-clone/buildah | Namen verschillen per versie. Check met `tkn task describe git-clone -n openshift-pipelines`. |
| `maven-build`: permission denied in de workspace | Wordt soms veroorzaakt door verschillende UIDs tussen pods. Voeg in de PipelineRun `taskRunTemplate.podTemplate.securityContext.fsGroup` toe, of vraag de namespace-UID op met `oc describe project`. |
| Pod blijft op *ImagePullBackOff* | Image niet gepusht of verkeerde namespace in de image-naam: `oc get is`. |
| Pod niet *Ready* | Bekijk de log: `oc logs deploy/hello-liberty`. Liberty logt welke features en apps gestart zijn. |
