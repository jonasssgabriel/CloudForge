# Decisões do projeto

Sempre que havia mais de um caminho, escolhemos o mais simples de explicar na prova de 11/12.

| # | Decisão | Por quê | Onde |
|---|---|---|---|
| 1 | 3 projetos Maven separados (nomes, servidor, cliente) | É o estilo da aula e cada um abre sozinho no NetBeans. | `ServicoNomes/`, `ServidorCloudForge/`, `ClienteCloudForge/` |
| 2 | Cada projeto tem a sua cópia dos `.proto`, mantidas idênticas | Mesmo padrão do trabalho anterior. O custo é copiar na mão se mudar. | `src/main/proto` de cada projeto |
| 3 | Fatorial com `BigInteger` (N de 0 a 5000) em vez de Fibonacci | 21! já estoura `long`, então `BigInteger` justifica a escolha. Acima de 60 dígitos devolve só o começo e a quantidade de dígitos. | `CloudForgeServiceImpl.fatorial` |
| 4 | No nomes, `LinkedHashMap` acessado só por métodos `synchronized` curtos (não `ConcurrentHashMap`) | É mais fácil de explicar e suficiente. Também mantém a ordem de registro. | `NomesServiceImpl.guardar`, `remover`, `copiaInstancias` |
| 5 | Registrar com o mesmo `id` substitui o anterior | Cobre o reinício de uma instância sem ficar duplicada. | `NomesServiceImpl.guardar` |
| 6 | Instância que cai sem desregistrar continua na lista do nomes | O nomes só diz ONDE. Quem decide se está viva é o Health Check do cliente. | `NomesServiceImpl.listar` (comentário) |
| 7 | Um único `lock` por instância para contadores, histórico e `emAndamento` | Com um lock só não há risco de ler o estado pela metade. | `CloudForgeServiceImpl` (campo `lock`) |
| 8 | Processamento, `sleep` e chamada de rede sempre fora do lock | Senão as tarefas andariam uma de cada vez. | `CloudForgeServiceImpl.executarTarefa` |
| 9 | Limite de simultâneas por contador `emAndamento` sob o lock, sem `Semaphore` | Mesmo lock do resto, fácil de explicar. Rejeita com `RESOURCE_EXHAUSTED` em vez de fazer fila. | `CloudForgeServiceImpl.executarTarefa` |
| 10 | Validação antes de ocupar vaga | Entrada inválida não deve gastar capacidade. | `CloudForgeServiceImpl.validar` |
| 11 | A vaga é liberada num `finally` | Aconteça o que acontecer (cancelamento, erro), `emAndamento` não vaza. | `CloudForgeServiceImpl.executarTarefa` |
| 12 | Atraso simulado em fatias de 200 ms checando `Context.current().isCancelled()` | Mesmo padrão do trabalho anterior. Permite o servidor parar quando o cliente desiste. | `CloudForgeServiceImpl.executarTarefa` |
| 13 | Round-robin e Health Check manuais, sem `round_robin` nem `healthCheckConfig` do gRPC | É o que o enunciado pede (seção 3.5). | `ClienteCore.proximoServidorSaudavel` |
| 14 | Só `avancarIndice` é `synchronized`; o `Health.Check` fica fora | Seguimos o exemplo do enunciado. Rede dentro do lock serializaria os clientes. | `ClienteCore.avancarIndice` |
| 15 | Um canal e stubs por servidor, criados uma única vez, e `atualizar` reaproveita | Criar canal a cada chamada é caro. | `ClienteCore.Servidor`, `atualizar` |
| 16 | Deadlines: 2 s no Health, 5 s na tarefa, 3 s no Listar e nas estatísticas, 2 s no registro | Dentro da faixa de 2 a 5 s do enunciado. | `ClienteCore`, `ServidorCloudForge` |
| 17 | Retry de 1 vez em `DEADLINE_EXCEEDED`, `UNAVAILABLE` e `RESOURCE_EXHAUSTED`, no próximo servidor saudável | As tarefas são idempotentes (não mudam nada além de contadores e histórico), então repetir é seguro. `INVALID_ARGUMENT` não tenta de novo. | `ClienteCore.enviar` |
| 18 | Se nenhum servidor está saudável, o cliente re-consulta o nomes uma vez antes de desistir | Pode ter entrado uma instância nova. | `ClienteCore.enviar` |
| 19 | O Health marca `""` e o nome do serviço | Cobre quem consulta o servidor inteiro e quem consulta o serviço. | `ServidorCloudForge.definirHealth` |
| 20 | `encerrar()` roda uma vez só (`AtomicBoolean`) | O comando `sair` e o shutdown hook podem chamar os dois. | `ServidorCloudForge.encerrar` |
| 21 | Se o nomes estiver fora ao subir, o servidor só avisa e continua | Não vale derrubar o servidor por isso. | `ServidorCloudForge.main` |
| 22 | Bônus por flag `--global`, desligado por padrão | Não atrapalha a demonstração base. | `ServidorCloudForge.main`, `CloudForgeServiceImpl.executarTarefa` |
| 23 | Bônus: histórico global fica no nomes (serviço central), limitado a 5000 registros | É a forma mais simples de sincronizar entre processos. Se falhar, só loga. | `NomesServiceImpl.registrarProcessamento` |
| 24 | Campos extras no `.proto` além do prompt inicial: RPCs `RegistrarProcessamento` e `ObterHistoricoGlobal` e as mensagens `RegistroGlobal` e `HistoricoGlobal` | Necessários só para o bônus. | `nomes.proto` |
| 25 | Cliente separado em `ClienteCore` (lógica) e `ClienteCloudForge` (console) | A GUI e o console usam a mesma lógica, sem duplicar round-robin e health. | `ClienteCore`, `ClienteCloudForge`, `ClienteGUI` |
| 26 | Scripts `.bat` usam `java -cp target\classes;<cp.txt>` em vez de `mvn exec:java` | Sobe mais rápido e não depende do plugin exec. O `00` gera o `cp.txt`. | `scripts/` |
| 27 | GUI Swing com tema claro, sem biblioteca extra, além do console | O enunciado aceita Swing ou console. A GUI ajuda a mostrar o round-robin e a saúde ao vivo. | `ClienteGUI`, `TelaCloudForge` |
| 28 | `CorridaDemo` como classe `main` separada (sem JUnit) | Para mostrar na prova que sem `synchronized` o contador perde atualizações. O servidor real fica sempre protegido. | `CorridaDemo.main` |
| 29 | Sem commit nem push | O Jonas publica no GitHub depois. | n/a |
