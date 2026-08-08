param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$sourceRoot = Join-Path $RepositoryRoot 'src'
$outputPath = Join-Path $RepositoryRoot 'doc\AI_API_INDEX.md'
$javaFiles  = Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' | Sort-Object FullName
$entries    = [System.Collections.Generic.List[object]]::new()

foreach ($file in $javaFiles) {
    $text = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
    $packageMatch = [regex]::Match($text, '(?m)^\s*package\s+([\w.]+)\s*;')
    $typeMatch = [regex]::Match($text, '(?m)^\s*(?:(public)\s+)?(?:(?:abstract|final|sealed|non-sealed|static)\s+)*(class|interface|enum|record|@interface)\s+(\w+)')
    if (-not $packageMatch.Success -or -not $typeMatch.Success) {
        continue
    }

    $packageName = $packageMatch.Groups[1].Value
    $typeName    = $typeMatch.Groups[3].Value
    $beforeType  = $text.Substring(0, $typeMatch.Index)
    $docMatches  = [regex]::Matches($beforeType, '(?s)/\*\*(.*?)\*/')
    $summary     = '소스의 public API와 호출부를 확인하여 사용'

    if ($docMatches.Count -gt 0) {
        $rawSummary = $docMatches[$docMatches.Count - 1].Groups[1].Value
        $cleanLines = $rawSummary -split "`r?`n" |
            ForEach-Object { $_ -replace '^\s*\*\s?', '' } |
            Where-Object { $_ -and $_ -notmatch '^@' -and $_ -notmatch '^<' }
        if ($cleanLines.Count -gt 0) {
            $summary = (($cleanLines | Select-Object -First 2) -join ' ') -replace '\|', '\|'
            $summary = $summary.Trim()
        }
    }

    $withoutComments = [regex]::Replace($text, '(?s)/\*.*?\*/|//[^\r\n]*', ' ')
    $methodMatches = [regex]::Matches(
        $withoutComments,
        '(?ms)^\s*public\s+(?!class\b|interface\b|enum\b|record\b|@interface\b)(?:(?:static|final|synchronized|abstract|default|native|strictfp)\s+)*(?:<[^;{}()]+>\s*)?[\w.$<>?\[\], ]+\s+(\w+)\s*\((.*?)\)\s*(?:throws\s+[^\{;]+)?[\{;]'
    )
    $methods = $methodMatches |
        ForEach-Object { $_.Groups[1].Value } |
        Where-Object { $_ -ne $typeName } |
        Select-Object -Unique

    $relativePath = $file.FullName.Substring($RepositoryRoot.Length + 1).Replace('\', '/')
    $entries.Add([pscustomobject]@{
        Package = $packageName
        Type = $typeName
        Kind = $typeMatch.Groups[2].Value
        Visibility = if ($typeMatch.Groups[1].Success) { 'public' } else { 'package-private' }
        Summary = $summary
        Methods = if ($methods.Count -gt 0) { ($methods -join ', ') } else { '-' }
        Path = $relativePath
    })
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# TX24_LIB AI API Index')
$lines.Add('')
$lines.Add('> 이 문서는 AI가 TX24_LIB의 기존 기능을 먼저 발견하고 재사용하도록 돕는 색인입니다.')
$lines.Add('> 생성: `powershell -ExecutionPolicy Bypass -File bin/generate-ai-api-index.ps1`')
$lines.Add('> public API의 정확한 계약은 반드시 링크된 소스, 테스트, 실제 호출부에서 다시 확인하십시오.')
$lines.Add('')
$lines.Add("- Java source types: $($entries.Count)")
$lines.Add('- 기본 검색: `rg -n "기능명|메서드명" D:\worktm\TX24_LIB\src D:\worktm\TX24_LIB\test`')
$lines.Add('- 호출 예제 검색: `rg -n "ClassName|methodName" D:\worktm\TX24_LIB --glob "*.java"`')
$lines.Add('')
$lines.Add('## 사용 판단 순서')
$lines.Add('')
$lines.Add('1. 아래 패키지와 클래스 색인에서 후보를 찾는다.')
$lines.Add('2. 링크된 소스에서 시그니처, null/빈 값, 예외, 인코딩, 스레드 안전성 정책을 확인한다.')
$lines.Add('3. `test` 및 전체 저장소 호출부를 검색해 실제 사용 방식을 확인한다.')
$lines.Add('4. 적합한 API가 있으면 프로젝트 자체 구현보다 우선 사용한다.')
$lines.Add('5. 적합한 API가 없고 반복 가능성이 높으면 TX24_LIB 확장 후보를 제안한다.')
$lines.Add('')
$lines.Add('## 기능별 빠른 경로')
$lines.Add('')
$lines.Add('| 기능 | 우선 확인 패키지 |')
$lines.Add('|---|---|')
$lines.Add('| 문자열, 날짜, 숫자, URI, IP, 마스킹, 보안 보조 | `kr.tx24.lib.lang` |')
$lines.Add('| Map 생성, typed map, 공유 및 만료 캐시 | `kr.tx24.lib.map` |')
$lines.Add('| JSON, XML, YAML, CSV 변환 | `kr.tx24.lib.mapper` |')
$lines.Add('| DB 연결, CRUD builder, transaction, result set | `kr.tx24.lib.db` |')
$lines.Add('| Redis, typed codec, cache, pub/sub, queue | `kr.tx24.lib.redis` |')
$lines.Add('| 암호화, 해시, 비밀번호, KMS | `kr.tx24.lib.cipher`, `kr.tx24.lib.crypt`, `kr.tx24.lib.kms` |')
$lines.Add('| 비동기 실행, 종료 및 JVM lifecycle | `kr.tx24.lib.executor`, `kr.tx24.lib.lifecycle` |')
$lines.Add('| HTTP/Netty 서버, route, controller, response | `kr.tx24.inet` |')
$lines.Add('| 작업 예약 및 실행 | `kr.tx24.task` |')
$lines.Add('| 압축, HTML, 로깅, 부하분산 | `kr.tx24.lib.zip`, `kr.tx24.lib.jsoup`, `kr.tx24.lib.logback`, `kr.tx24.lib.lb` |')

foreach ($packageGroup in ($entries | Group-Object Package | Sort-Object Name)) {
    $lines.Add('')
    $lines.Add(('## `{0}`' -f $packageGroup.Name))
    $lines.Add('')
    $lines.Add('| 클래스 | 접근 | 종류 | 역할 | 대표 public 메서드 |')
    $lines.Add('|---|---|---|---|---|')
    foreach ($entry in ($packageGroup.Group | Sort-Object Type)) {
        $methods = $entry.Methods -replace '\|', '\|' -replace '<', '&lt;' -replace '>', '&gt;'
        if ($methods.Length -gt 240) {
            $methods = $methods.Substring(0, 237) + '...'
        }
        $lines.Add(('| [`{0}`](../{1}) | {2} | {3} | {4} | `{5}` |' -f $entry.Type, $entry.Path, $entry.Visibility, $entry.Kind, $entry.Summary, $methods))
    }
}

$lines.Add('')
$lines.Add('## 유지보수 규칙')
$lines.Add('')
$lines.Add('- public 클래스를 추가·이름 변경·삭제하거나 public API를 크게 변경하면 생성 스크립트를 다시 실행한다.')
$lines.Add('- 색인은 탐색용이며 API 계약을 대체하지 않는다. 구현 전 소스와 테스트 확인은 필수다.')
$lines.Add('- 여러 프로젝트에서 반복되는 기능은 가장 가까운 기존 패키지와 클래스에 하위 호환 방식으로 추가할 것을 권고한다.')

$outputDirectory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
[System.IO.File]::WriteAllLines($outputPath, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Generated $outputPath with $($entries.Count) source types."
