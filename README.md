# CloudForge: processamento distribuído com gRPC

Trabalho de SPD 2026-2 (serviço distribuído com gRPC, client-side load balancing).
Grupo do Jonas. Datas: 27/11 andamento, 04/12 entrega no GitHub, 11/12 prova individual.

## Visão geral

O CloudForge recebe tarefas de processamento e distribui entre várias instâncias de um mesmo servidor. Quem escolhe a instância é o **cliente** (round-robin escrito à mão, sem o balanceamento automático do gRPC). O sistema tem 3 projetos Maven separados:

| Projeto | Pacote | Papel |
|---|---|---|
| `ServicoNomes` | `com.mycompany.servicodenomes` | Diz ONDE estão os servidores (porta 50050). |
| `ServidorCloudForge` | `com.mycompany.servidorcloudforge` | Executa as tarefas. Sobe quantas instâncias quiser (`srv-<porta>`). |
| `ClienteCloudForge` | `com.mycompany.clientecloudforge` | Consulta o nomes, faz Health Check, faz o round-robin e envia as tarefas. Tem console e GUI. |

Tarefas do servidor:

| Tipo | Entrada | Saída |
|---|---|---|
| `CALCULO` | número N (0 a 5000) | fatorial com `BigInteger` (resumo se passar de 60 dígitos) |
| `PROCESSAMENTO_DADOS` (`DADOS` no cliente) | lista de números separados por vírgula | soma, média, mínimo, máximo e lista ordenada |
| `HASH` | texto | SHA-256 em hexadecimal |

Cada instância tem estado próprio: contador `proximoIdTarefa`, contadores `recebidas/concluidas/rejeitadas`, histórico (últimos 1000) e limite de tarefas simultâneas (padrão 4). Duas instâncias geram o mesmo `#1`, e isso é proposital (estado local x global).

## Arquitetura

```
                     +----------------------+
                     |    ServicoNomes      |
                     |     porta 50050      |
                     +----------------------+
                       ^         ^        ^
        Registrar /    |         |        |  Listar
        Desregistrar   |         |        |  (deadline 3 s)
                       |         |        |
        +--------------+    +----+--------+--+
        |                   |                |
+---------------+   +---------------+   +---------------+
| srv-50051     |   | srv-50052     |   | srv-50053     |
| CloudForge    |   | CloudForge    |   | CloudForge    |
| + Health      |   | + Health      |   | + Health      |
+---------------+   +---------------+   +---------------+
        ^                   ^                   ^
        |   1) Health.Check (deadline 2 s)      |
        |   2) ExecutarTarefa (deadline 5 s)    |
        +-------------------+-------------------+
                            |
                  +--------------------+
                  | ClienteCloudForge  |
                  | round-robin manual |
                  +--------------------+
```

Os três papéis do enunciado:

- **Nomes** diz ONDE estão os servidores.
- **Health Check** diz QUAIS estão saudáveis.
- **Round-robin** decide PARA QUAL vai cada chamada.

Nenhuma porta de servidor está fixa no cliente. A única coisa fixa é o endereço do nomes (`localhost:50050`), que pode ser trocado por argumento.

## Pré-requisitos

- JDK 26 (o do NetBeans serve) e Maven 3.9.
- No Windows `java` e `mvn` não estão no PATH. Em cada terminal:

```powershell
$env:JAVA_HOME = "C:\Program Files\Apache NetBeans\jdk"
$env:Path = "$env:JAVA_HOME\bin;C:\Program Files\Apache NetBeans\java\maven\bin;$env:Path"
```

No `cmd`: `set "JAVA_HOME=C:\Program Files\Apache NetBeans\jdk"` e `set "PATH=%JAVA_HOME%\bin;C:\Program Files\Apache NetBeans\java\maven\bin;%PATH%"`. Os scripts `.bat` já fazem isso sozinhos.

- Versões: `maven.compiler.release=26`, gRPC 1.75.0, protobuf 3.25.5. O `grpc-services` (Health) está nos poms do servidor e do cliente.
- Os `.proto` ficam em `src/main/proto` de cada projeto. **Cada projeto tem a sua cópia e elas são idênticas** (`nomes.proto` e `cloudforge.proto`). Se mexer em uma, copie para as outras.

## Como compilar e rodar

### Pelos scripts (jeito mais rápido)

Pasta `scripts/`. Rode o `00` primeiro, uma vez. Cada script abre um processo que fica rodando, então use um terminal (ou janela) por script.

| Script | O que faz |
|---|---|
| `00-compilar-tudo.bat` | `mvn -q clean package` nos 3 projetos e gera `target\cp.txt` (classpath das dependências). |
| `01-nomes.bat` | Sobe o serviço de nomes (50050). |
| `02-servidor-50051.bat` | Sobe `srv-50051`. |
| `03-servidor-50052.bat` | Sobe `srv-50052`. |
| `04-cliente.bat` | Abre o cliente de console. |
| `05-servidor-50053.bat` | Sobe `srv-50053` (útil para mostrar uma terceira instância). |
| `06-cliente-gui.bat` | Abre o cliente gráfico (Swing). |

Os scripts de servidor repassam argumentos extras, então `02-servidor-50051.bat --global --limite=2` funciona. Por baixo eles rodam `java -cp target\classes;<cp.txt> <classe principal>`.

Ordem normal: `00`, depois `01`, `02`, `03` e por fim `04`.

### Pela linha de comando (sem script)

```powershell
cd ServicoNomes
mvn -q clean package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
$CP = Get-Content target\cp.txt
java -cp "target\classes;$CP" com.mycompany.servicodenomes.ServicoNomes
```

Para o servidor troque a pasta e a classe: `com.mycompany.servidorcloudforge.ServidorCloudForge 50051`. Para o cliente: `com.mycompany.clientecloudforge.ClienteCloudForge`.

### Pelo NetBeans

1. Abra os 3 projetos (File > Open Project).
2. Clean and Build em cada um (baixa o gRPC e gera as classes do `.proto`).
3. `ServicoNomes`: Run File em `ServicoNomes.java`.
4. `ServidorCloudForge`: Run File em `ServidorCloudForge.java`. Para a segunda instância, o argumento da porta vai em Project Properties > Run > Arguments (ou use o script `03`).
5. `ClienteCloudForge`: Run File em `ClienteCloudForge.java` (console) ou em `ClienteGUI.java` (tela).

### Argumentos do servidor

```
ServidorCloudForge <porta> [enderecoNomes=localhost:50050] [host=localhost] [--global] [--limite=N]
```

As flags podem estar em qualquer posição. O id da instância é `srv-<porta>`.

## Cliente gráfico (GUI Swing)

![Cliente gráfico do CloudForge](docs/img/gui-cliente.png)

Além do console, existe uma tela Swing (tema claro) com a topologia animada (nomes, instâncias e o cliente), tabela das instâncias com a saúde, botões Enviar, `rr 6`, Concorrência e **Executar demonstração** (roteiro automático: `rr 6`, pausa, concorrência 50 e conferência no fim). Classes: `ClienteGUI` (main), `TelaCloudForge`, `PainelTopologia`, `PainelInstancias`, `PainelRodizio`, `PainelLog` e `TemaCloudForge`. A GUI usa o mesmo `ClienteCore` do console, então o round-robin e o Health Check são os mesmos.

Como abrir:

- Linha de comando, na pasta `ClienteCloudForge`, depois de rodar `00-compilar-tudo.bat`:

```
java -cp target\classes;%CP% com.mycompany.clientecloudforge.ClienteGUI
```

(`%CP%` é o conteúdo de `target\cp.txt`, como em `04-cliente.bat`: `set /p CP=<target\cp.txt`.)

- NetBeans: Run File em `ClienteGUI.java`.
- Argumento opcional: o endereço do nomes (padrão `localhost:50050`).

O console continua disponível (`04-cliente.bat`) e é ele que usamos no roteiro de testes abaixo.

## Comandos

### Console do servidor

| Comando | O que faz |
|---|---|
| `serving` | Marca `""` e o serviço `CloudForgeService` como SERVING. |
| `notserving` | Marca os dois como NOT_SERVING (o cliente passa a pular a instância). |
| `atraso <ms>` | Atraso simulado em cada tarefa (para testar deadline). `atraso 0` desliga. |
| `limite <n>` | Limite de tarefas simultâneas. Acima disso responde `RESOURCE_EXHAUSTED`. |
| `stats` | Recebidas, concluídas, rejeitadas, em andamento e limite. |
| `historico` | Últimas 20 tarefas da instância. |
| `ajuda` | Lista os comandos. |
| `sair` | Desregistra do nomes, marca NOT_SERVING e encerra. |

### Console do cliente

| Comando | O que faz |
|---|---|
| `listar` / `atualizar` | Consulta o serviço de nomes de novo e mostra as instâncias. |
| `enviar <CALCULO\|DADOS\|HASH> <entrada>` | Envia uma tarefa pelo round-robin com Health Check. |
| `rr <n>` | Envia n tarefas seguidas (tipos alternados). |
| `concorrencia <n>` | Dispara n tarefas em threads ao mesmo tempo e confere ids repetidos e total. |
| `stats` | Estatísticas de cada instância. |
| `historico` | Histórico de cada instância. |
| `historicoglobal` | Histórico global guardado no nomes (bônus, precisa de `--global`). |
| `ajuda` / `sair` | Ajuda e encerramento. |

Observação: a ordem em que o cliente lista as instâncias depende da ordem em que elas se registraram no nomes. Se subir a 50052 antes da 50051, o rodízio começa por ela. Os prazos são: 5 s na tarefa, 2 s no Health Check, 3 s no Listar.

## Roteiro de demonstração (os 9 testes)

1. **Nomes e 2 instâncias.** Sobe `01`, `02`, `03`. O log do nomes mostra `Registrada: srv-50051` e `srv-50052`.
2. **Lista sem porta fixa.** Abre o cliente (`04`). Ele mostra a lista vinda do nomes. As portas não aparecem no código do cliente.
3. **Round-robin.** `rr 6` alterna entre as instâncias (A, B, A, B, A, B). Cada resposta mostra `respondida por srv-...`.
4. **NOT_SERVING e SERVING.** No console de uma instância, `notserving`. O cliente imprime `[CLIENTE] srv-... pulado (NOT_SERVING)` e só usa a outra. Depois `serving` e ela volta ao rodízio.
5. **Queda.** Com o cliente aberto, `sair` (ou Ctrl+C) em uma instância. O cliente continua com a que sobrou (Health Check dá erro e pula).
6. **Nenhum saudável.** `notserving` nas duas (ou fechar as duas). O cliente re-consulta o nomes uma vez e então mostra `Nenhum servidor saudável no momento. Tente novamente.`
7. **Deadline.** Em uma instância `atraso 7000` (maior que os 5 s). O cliente trata `DEADLINE_EXCEEDED`, tenta o próximo servidor saudável uma vez e não trava. Depois `atraso 0`.
8. **Concorrência.** `concorrencia 200`: sem ids repetidos e com a soma de concluídas batendo. Depois `limite 2` em uma instância e `concorrencia 200` de novo para ver `RESOURCE_EXHAUSTED` tratado e contado em `rejeitadas`.
9. **Estado local x global.** Com as duas instâncias, `stats` e `historico` mostram que cada uma gerou o seu `#1` (os recursos são independentes). Para compartilhar entre instâncias é preciso um serviço central (bônus).

**Teste da corrida (CorridaDemo).** No projeto `ServidorCloudForge`, `CorridaDemo` roda 8 threads x 100000 incrementos, sem e com `synchronized`. Esperado: 800000. Sem lock o resultado fica abaixo disso; com lock dá 800000.

```
java -cp target\classes;%CP% com.mycompany.servidorcloudforge.CorridaDemo
```

### Resumo dos testes

Os testes T1 a T9, o bônus `--global` e o `CorridaDemo` foram executados com sucesso. Resumo: `rr 6` alternou entre as instâncias; `notserving`/`serving` e queda foram tratados; sem servidor saudável o cliente avisa; o deadline (atraso 7000) foi tratado; `concorrencia 200` terminou sem ids repetidos; com `limite 2` apareceu `RESOURCE_EXHAUSTED`; no `CorridaDemo` o contador sem lock deu entre 127027 e 150818 e com lock deu 800000. As saídas completas ficam em [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

## Bônus: histórico global (`--global`)

Com `--global` o servidor, depois de concluir cada tarefa e **fora do lock local**, envia o registro ao `ServicoNomes` (`RegistrarProcessamento`, deadline 2 s). Se o nomes falhar, só loga um aviso e a tarefa não é derrubada. O nomes guarda os últimos 5000 registros sob `synchronized` e expõe `ObterHistoricoGlobal`. No cliente, o comando `historicoglobal` mostra o total e os últimos 20. Sem a flag, tudo continua como no trabalho base. Explicação de sincronizar threads x processos em `docs/EXPLICACAO-PARA-PROVA.md`.

## Conformidade com o enunciado (PDF da professora)

### Seção 2: checklist

| Item | Como atendemos | Arquivo / método |
|---|---|---|
| Servidor Java com pelo menos 2 instâncias | Mesmo código sobe em portas diferentes; id `srv-<porta>`. | `ServidorCloudForge.main` |
| Registro em serviço de nomes e cliente descobre a lista | Servidor chama `registrar` ao subir; cliente chama `listar`. | `ServidorCloudForge.main` (registrar), `NomesServiceImpl.registrar/listar`, `ClienteCore.atualizar` |
| `grpc.health.v1.Health` registrado e funcionando | `HealthStatusManager` adicionado ao `ServerBuilder`; status `""` e do serviço. | `ServidorCloudForge.main`, `ServidorCloudForge.definirHealth` |
| Round-robin com `Health.Check` antes de cada requisição | Índice circular e check com deadline 2 s. | `ClienteCore.proximoServidorSaudavel`, `ClienteCore.avancarIndice` |
| Evitar condição de corrida entre threads da instância (bônus: entre instâncias) | Um lock único para contadores, histórico e `emAndamento`; bônus por `--global`. | `CloudForgeServiceImpl.executarTarefa`, `estatisticas`, `copiaHistorico`; `NomesServiceImpl.registrarProcessamento` |
| Chamada com deadline e tratamento de falha | 5 s na tarefa, 2 s no health, 3 s no listar; trata `DEADLINE_EXCEEDED`, `UNAVAILABLE`, `RESOURCE_EXHAUSTED`, `INVALID_ARGUMENT`. | `ClienteCore.enviar`, `ClienteCore.mensagemErro` |

### Seção 3: requisitos do servidor

| Item | Como atendemos | Arquivo / método |
|---|---|---|
| 3.1 Java, serviço definido em `.proto`, 2 ou mais instâncias | `cloudforge.proto` e `nomes.proto`; porta por argumento. | `cloudforge.proto`, `CloudForgeServiceImpl`, `ServidorCloudForge.main` |
| 3.2 Recurso compartilhado com controle de concorrência | `proximoIdTarefa`, contadores, `historico` e `emAndamento` sob o mesmo `lock`; processamento fora do lock. | `CloudForgeServiceImpl.executarTarefa` |
| 3.2 Instâncias com memória própria (bônus: compartilhar) | Estado local por instância; bônus via serviço central. | `CloudForgeServiceImpl` (campos), `NomesServiceImpl.obterHistoricoGlobal` |
| 3.3 Health com SERVING e NOT_SERVING, `grpc-services` no pom | `serving` e `notserving` no console. | `ServidorCloudForge.iniciarConsole`, `definirHealth`; `pom.xml` |
| 3.4 Serviço de nomes (onde), health (quais), round-robin (para qual) | Três papéis separados; o nomes não faz health check. | `NomesServiceImpl.listar` (comentário), `ClienteCore` |
| 3.5 Client-side LB manual: canal e stub uma vez, índice, `Health.Check` com deadline, pular se não SERVING, avisar se nenhum | Classe interna `Servidor` guarda canal e stubs; `atualizar` reaproveita canais; só `avancarIndice` é `synchronized`. | `ClienteCore.Servidor`, `atualizar`, `avancarIndice`, `proximoServidorSaudavel`, `enviar` |

### Seção 4: deadline e tratamento de falhas

| Item | Como atendemos | Arquivo / método |
|---|---|---|
| Toda chamada com deadline (2 a 5 s) | Listar 3 s, health 2 s, tarefa 5 s, stats 3 s. | `ClienteCore.atualizar`, `proximoServidorSaudavel`, `enviar`, `estatisticas` |
| Capturar o erro | `catch (StatusRuntimeException)` pelo código de status. | `ClienteCore.enviar` |
| Tratar (mensagem, registro, outro servidor) | Mensagem amigável, log `[CLIENTE]` e uma nova tentativa no próximo saudável. | `ClienteCore.enviar`, `mensagemErro` |
| Continuar com as instâncias que restam | Health falhou, pula e segue o rodízio. | `ClienteCore.proximoServidorSaudavel` |
| Servidor respeita o cancelamento | Atraso em fatias de 200 ms checando `Context.current().isCancelled()`. | `CloudForgeServiceImpl.executarTarefa` |

### Seção 6: como testar (demonstração)

| Teste | Como mostramos | Arquivo / método |
|---|---|---|
| Subir nomes e 2 instâncias | Log `[NOMES] Registrada: ...`. | `NomesServiceImpl.registrar` |
| Lista sem porta fixa | Cliente imprime a lista do nomes. | `ClienteCore.atualizar` |
| Round-robin com 6 requisições | `rr 6`, cada resposta indica a instância. | `ClienteCloudForge.rodizio`, `ClienteCore.enviar` |
| NOT_SERVING e volta a SERVING | Comandos `notserving` e `serving`; cliente loga `pulado`. | `ServidorCloudForge.iniciarConsole`, `ClienteCore.proximoServidorSaudavel` |
| Queda de instância | `sair` ou Ctrl+C; o health dá erro e o cliente pula. | `ServidorCloudForge.encerrar`, `ClienteCore.proximoServidorSaudavel` |
| Nenhum saudável | Re-consulta o nomes uma vez e mostra a mensagem. | `ClienteCore.enviar` |
| Deadline maior que o atraso | `atraso 7000`; `DEADLINE_EXCEEDED` tratado. | `CloudForgeServiceImpl.executarTarefa`, `ClienteCore.enviar` |
| Concorrência sem valores repetidos ou perdidos | `concorrencia 200`; confere ids repetidos e soma de concluídas. | `ClienteCore.concorrencia` |
| Estado local x global | `stats`: as duas geram `#1`. | `ClienteCore.estatisticas`, `CloudForgeServiceImpl.estatisticas` |

### Seção 7: entrega

| Item | Onde está |
|---|---|
| Código-fonte completo (servidor, nomes, cliente e `.proto`) | `ServidorCloudForge/`, `ServicoNomes/`, `ClienteCloudForge/` e `src/main/proto` de cada um |
| `README.md` com instruções de execução | este arquivo |
| Repositório no GitHub até 04/12 | o Jonas publica depois (não fizemos commit nem push) |

## Estrutura

```
CloudForge/
  README.md   PROMPTS.md
  docs/       DECISOES.md  EXPLICACAO-PARA-PROVA.md  EVIDENCIAS.md
  scripts/    00-compilar-tudo.bat  01..05 (nomes, servidores, cliente)
  ServicoNomes/  ServidorCloudForge/  ClienteCloudForge/
```
