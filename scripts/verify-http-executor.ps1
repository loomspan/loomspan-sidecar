param([string]$Test = '')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$check = Join-Path $repo 'target/http-executor-check'
New-Item -ItemType Directory -Force -Path $check | Out-Null
[xml]$pom = Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw
$ns = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
$ns.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
$project = $pom.DocumentElement
$build = $project.SelectSingleNode('m:build', $ns)
$project.RemoveChild($build) | Out-Null
$profiles = $project.SelectSingleNode('m:profiles', $ns)
if ($profiles) { $project.RemoveChild($profiles) | Out-Null }
$source = (Join-Path $repo 'src/main/java').Replace('\','/')
$tests = (Join-Path $repo 'src/test/java').Replace('\','/')
$fragment = $pom.CreateDocumentFragment()
$fragment.InnerXml = @"
<build xmlns="http://maven.apache.org/POM/4.0.0">
 <sourceDirectory>$source</sourceDirectory><testSourceDirectory>$tests</testSourceDirectory>
 <plugins>
  <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version>
   <configuration><includes><include>ai/loomspan/sidecar/http/*.java</include><include>ai/loomspan/sidecar/configuration/ProviderCredentialCipher.java</include><include>ai/loomspan/sidecar/storage/EncryptedCredential.java</include></includes><testIncludes><testInclude>ai/loomspan/sidecar/http/*.java</testInclude></testIncludes></configuration>
  </plugin>
  <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.4</version><configuration><useModulePath>false</useModulePath><reuseForks>false</reuseForks></configuration></plugin>
 </plugins>
</build>
"@
$project.AppendChild($fragment) | Out-Null
$path = Join-Path $check 'pom.xml'
$pom.Save($path)
[System.IO.File]::WriteAllText($path, ([System.IO.File]::ReadAllText($path)).Replace('<build xmlns="http://maven.apache.org/POM/4.0.0">', '<build>'))
Write-Host 'ISOLATED HTTP RUNTIME CHECK: excludes unfinished startup/management; this is not a full build.'
$arguments = @('-q','-f',$path,'clean','test')
if ($Test) { $arguments += "-Dtest=$Test" }
& (Join-Path $repo 'mvnw.cmd') @arguments
exit $LASTEXITCODE
