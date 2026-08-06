#requires -Version 5.1
<#
.SYNOPSIS
    Build, push, and deploy the dental-pms backend to Cloud Run for a given environment.

.DESCRIPTION
    1. Builds the Docker image from the repo root (the multi-stage Dockerfile).
    2. Pushes it to Artifact Registry.
    3. Renders deploy/cloudrun/service.<env>.yaml by substituting __IMAGE__ with the pushed image.
    4. Applies it with `gcloud run services replace`.
    5. Prints the resulting service URL.

    One-time GCP setup (Artifact Registry repo, Secret Manager secrets, IAM) is documented in
    docs/environments.md. You must be authenticated: `gcloud auth login` and, for docker push,
    `gcloud auth configure-docker <REGION>-docker.pkg.dev`.

.PARAMETER Environment
    Target environment: dev | prod. Selects deploy/cloudrun/service.<env>.yaml and service name.

.PARAMETER ProjectId
    GCP project id. Defaults to $env:GCP_PROJECT_ID.

.PARAMETER Region
    Cloud Run / Artifact Registry region, e.g. asia-southeast1. Defaults to $env:GCP_REGION.

.PARAMETER Repo
    Artifact Registry (Docker) repository name. Defaults to $env:GCP_AR_REPO.

.PARAMETER Tag
    Image tag. Defaults to the current git short SHA.

.EXAMPLE
    ./deploy/deploy.ps1 -Environment dev

.EXAMPLE
    $env:GCP_PROJECT_ID = 'my-proj'; $env:GCP_REGION = 'asia-southeast1'; $env:GCP_AR_REPO = 'dental-pms'
    ./deploy/deploy.ps1 -Environment prod
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('dev', 'prod')]
    [string]$Environment,

    [string]$ProjectId = $env:GCP_PROJECT_ID,
    [string]$Region    = $env:GCP_REGION,
    [string]$Repo      = $env:GCP_AR_REPO,
    [string]$Tag
)

$ErrorActionPreference = 'Stop'

function Fail([string]$msg) { Write-Error $msg; exit 1 }

# --- Resolve required config ------------------------------------------------
if ([string]::IsNullOrWhiteSpace($ProjectId)) { Fail 'Set -ProjectId or $env:GCP_PROJECT_ID (your GCP project id).' }
if ([string]::IsNullOrWhiteSpace($Region))    { Fail 'Set -Region or $env:GCP_REGION (e.g. asia-southeast1).' }
if ([string]::IsNullOrWhiteSpace($Repo))      { Fail 'Set -Repo or $env:GCP_AR_REPO (Artifact Registry repo name).' }

# --- Paths ------------------------------------------------------------------
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = (Resolve-Path (Join-Path $scriptDir '..')).Path
$manifest  = Join-Path $scriptDir "cloudrun/service.$Environment.yaml"
if (-not (Test-Path $manifest)) { Fail "Manifest not found: $manifest" }

# --- Image tag (git short SHA by default) -----------------------------------
if ([string]::IsNullOrWhiteSpace($Tag)) {
    $sha = (& git -C $repoRoot rev-parse --short HEAD)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($sha)) {
        Fail 'Could not resolve git short SHA for the image tag; pass -Tag explicitly.'
    }
    $Tag = $sha.Trim()
}

$image = "$Region-docker.pkg.dev/$ProjectId/$Repo/dental-pms:$Tag"

# The service name comes from the manifest rather than a naming convention: `services replace`
# addresses the service by metadata.name, so that is the only name that decides what gets deployed.
# (metadata.name is the sole `name:` key at two-space indent; the others sit deeper, under env/ports.)
$manifestRaw = Get-Content -Raw $manifest
$nameMatch = [regex]::Match($manifestRaw, '(?m)^  name:\s*(\S+)\s*$')
if (-not $nameMatch.Success) { Fail "Could not read metadata.name from $manifest." }
$service = $nameMatch.Groups[1].Value

Write-Host "==> Environment : $Environment"
Write-Host "==> Service     : $service"
Write-Host "==> Image       : $image"
Write-Host ""

# --- Build ------------------------------------------------------------------
Write-Host '==> docker build'
& docker build -t $image $repoRoot
if ($LASTEXITCODE -ne 0) { Fail 'docker build failed.' }

# --- Push -------------------------------------------------------------------
Write-Host '==> docker push'
& docker push $image
if ($LASTEXITCODE -ne 0) { Fail "docker push failed. Did you run: gcloud auth configure-docker ${Region}-docker.pkg.dev ?" }

# --- Render manifest (substitute __IMAGE__) ---------------------------------
$content = $manifestRaw.Replace('__IMAGE__', $image)
# Comment lines are excluded: the manifest's header documents the placeholders by name, so a
# whole-file match would refuse to deploy even once every real value has been filled in.
$placeholders = ($content -split "`r?`n") | Where-Object { $_ -match 'REPLACE_' -and $_ -notmatch '^\s*#' }
if ($placeholders) {
    Write-Host ($placeholders -join [Environment]::NewLine)
    Fail "$manifest still contains REPLACE_ placeholders. Fill in the Neon host and front-end origin before deploying."
}
$rendered = (New-TemporaryFile).FullName
# WriteAllText writes UTF-8 without a BOM, which gcloud's YAML parser needs.
[System.IO.File]::WriteAllText($rendered, $content)
Write-Host "==> Rendered manifest: $rendered"

# --- Deploy -----------------------------------------------------------------
Write-Host '==> gcloud run services replace'
& gcloud run services replace $rendered --region $Region --project $ProjectId
$deployExit = $LASTEXITCODE
Remove-Item $rendered -Force -ErrorAction SilentlyContinue
if ($deployExit -ne 0) { Fail 'gcloud run services replace failed.' }

# --- Report URL -------------------------------------------------------------
$url = (& gcloud run services describe $service --region $Region --project $ProjectId --format 'value(status.url)')
if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($url)) {
    $url = $url.Trim()
    Write-Host ""
    Write-Host "==> Deployed $service"
    Write-Host "==> URL:    $url"
    Write-Host "==> Health: $url/health"
} else {
    Write-Host ""
    Write-Host "==> Deployed $service (could not read service URL; check the Cloud Run console)."
}
