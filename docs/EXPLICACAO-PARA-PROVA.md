# Explicação para a prova (11/12)

Perguntas que a professora pode fazer, com resposta curta e onde está no código.

**1. Por que `synchronized` só no avanço do índice?**
O índice do rodízio é estado compartilhado: várias threads pedem "o próximo" ao mesmo tempo. Sem lock, duas pegariam o mesmo valor. No rodízio, a região crítica só reserva o ponto de partida. Há também blocos curtos para publicar a lista e contar seus usuários, sem chamadas de rede dentro deles.
Onde: `ClienteCore.avancarIndice`.

**2. Por que o Health Check fica fora do lock?**
Health.Check é chamada de rede e pode demorar até 2 s. Dentro do lock, todas as requisições do cliente esperariam uma pela outra e seriam atendidas uma de cada vez.
Onde: `ClienteCore.proximoServidorSaudavel`.

**3. Por que criar o canal uma única vez?**
Abrir canal gasta conexão e tempo. O `ManagedChannel` foi feito para ser reaproveitado por várias chamadas. Cada servidor tem canal e stubs guardados e o `atualizar` só cria os novos e fecha os que sumiram.
Onde: `ClienteCore.Servidor` (construtor), `ClienteCore.atualizar` e `ClienteCore.liberarServidores`. O canal retirado só fecha depois de terminar o último uso da lista antiga.

**4. Qual a diferença entre nomes, health e round-robin?**
Nomes diz ONDE estão os servidores. Health diz QUAIS estão saudáveis. Round-robin decide PARA QUAL vai cada chamada. O nomes não faz health check nem balanceamento.
Onde: `NomesServiceImpl.listar`, `ServidorCloudForge.definirHealth`, `ClienteCore.proximoServidorSaudavel`.

**5. DEADLINE_EXCEEDED, UNAVAILABLE e RESOURCE_EXHAUSTED?**
- `DEADLINE_EXCEEDED`: o prazo do cliente (5 s na tarefa, 2 s no health) acabou antes da resposta. O servidor pode estar lento ou parado.
- `UNAVAILABLE`: não deu para falar com o servidor (caiu, porta fechada, conexão recusada).
- `RESOURCE_EXHAUSTED`: o servidor está no ar, mas já atingiu o limite de tarefas simultâneas e recusou.
No cliente os três têm mensagem própria e uma nova tentativa no próximo servidor. `INVALID_ARGUMENT` (entrada ruim) não tenta de novo.
Onde: `ClienteCore.enviar` e `mensagemErro`; `CloudForgeServiceImpl.executarTarefa`.

**6. Por que `synchronized` não resolve entre instâncias?**
Cada instância é um processo com a sua memória e o seu lock. O `synchronized` só coordena threads do mesmo processo. Por isso duas instâncias geram, cada uma, o seu `#1`.
Onde: `CloudForgeServiceImpl` (campos `lock` e `proximoIdTarefa`); teste 9 do README.

**7. Por que a instância que cai continua na lista do nomes?**
Decisão de simplicidade: o nomes só guarda onde cada servidor registrou. Se o processo morre sem desregistrar, ele continua na lista. Quem trata isso é o cliente: o Health.Check falha e a instância é pulada. Em `sair` e Ctrl+C o servidor desregistra antes.
Onde: `NomesServiceImpl.listar`, `ServidorCloudForge.encerrar`, `ClienteCore.proximoServidorSaudavel`.

**8. Limite de simultâneas é a mesma coisa que fila?**
Não. Aqui, passando do limite o servidor rejeita na hora com `RESOURCE_EXHAUSTED` e conta em `rejeitadas`. Não guarda o pedido. Quem decide tentar outra instância é o cliente. Fila seria o servidor guardar o pedido e atender depois.
Onde: `CloudForgeServiceImpl.executarTarefa` (bloco que compara `emAndamento < limite`).

**9. Por que não segurar o lock durante o processamento?**
Se o fatorial ou o `sleep` ficassem dentro do `synchronized`, só uma tarefa rodaria por vez e o limite de simultâneas perderia o sentido. O lock só protege a atualização do estado (contadores, histórico, `emAndamento`), que é rápida.
Onde: `CloudForgeServiceImpl.executarTarefa` (o `processar` fica entre dois blocos `synchronized`).

**10. O que é idempotência e por que o retry é seguro?**
Uma operação idempotente pode ser repetida sem produzir novos efeitos além do primeiro. Aqui o cálculo de fatorial, estatística e SHA-256 dá a mesma resposta se repetido, mas a RPC completa também incrementa contadores e grava histórico, então não é estritamente idempotente. O cliente aceita esse efeito e tenta uma vez outra instância. Se a primeira tentativa estourou o deadline depois de concluir, a tarefa pode aparecer duas vezes no histórico. Isso é aceitável para esta demonstração, mas não seria para cobrança ou geração de senhas sem deduplicação.
Onde: `ClienteCore.enviar`.

**11. O que é condição de corrida e como o `CorridaDemo` mostra?**
É quando o resultado depende da ordem em que as threads rodam. `contador++` são três passos (ler, somar, gravar). Duas threads podem ler o mesmo valor e uma atualização se perde. O `CorridaDemo` roda 8 threads x 100000 incrementos: esperado 800000. Sem `synchronized` deu entre 127027 e 150818, e com `synchronized` deu 800000.
Onde: `CorridaDemo.main`.

**12. Como o `concorrencia 200` prova que está protegido?**
Dispara 200 tarefas em até 32 threads, soma as respostas bem-sucedidas, compara com o aumento de `concluidas` nas mesmas instâncias (`ObterEstatisticas`) e conta IDs repetidos por instância (tem que dar 0). É uma evidência do teste, não uma prova para toda execução possível. Se faltar uma amostra após queda, a comparação é inconclusiva; para comparar contadores, não deve haver outros clientes enviando tarefas ou reinício durante o teste.
Onde: `ClienteCore.concorrencia` e `ClienteCore.enviar` (conjunto `idsRespondidos`).

**13. O que muda no bônus?**
Com `--global`, cada servidor manda o registro ao `ServicoNomes`, que guarda um histórico único protegido por `synchronized`. Dois níveis de sincronização:
- **Threads (mesmo processo):** `synchronized` resolve, porque a memória é a mesma.
- **Processos (instâncias diferentes):** não há memória comum. É preciso um serviço central ou outro mecanismo, via chamada de rede.
O que fica de fora: (a) tolerância a falha do serviço central (se o nomes cair, o histórico global para; por isso o servidor só loga o aviso e segue); (b) consistência (o registro vai depois de concluir a tarefa, sem transação, então pode haver registro perdido ou atraso). E o contador `#id` de cada instância continua local.
Onde: `CloudForgeServiceImpl.executarTarefa` (bloco `global`), `NomesServiceImpl.registrarProcessamento` e `obterHistoricoGlobal`, `ClienteCloudForge.mostrarGlobal`.

**14. Por que o health marca `""` e o nome do serviço?**
O `""` é o status geral do servidor e o outro é o do serviço específico. O cliente consulta `""`. Marcamos os dois para ficar coerente.
Onde: `ServidorCloudForge.definirHealth`.

**15. E se o cliente desiste no meio da tarefa?**
O servidor espera em fatias de 200 ms e checa `Context.current().isCancelled()`. Se cancelou, devolve `CANCELLED` e o `finally` libera a vaga, então o limite não vaza.
Onde: `CloudForgeServiceImpl.executarTarefa`.

## Perguntas difíceis que a professora pode fazer

Os arquivos citados estão em `src/main/java/com/mycompany/clientecloudforge` no projeto ClienteCloudForge, `src/main/java/com/mycompany/servidorcloudforge` no ServidorCloudForge e `src/main/java/com/mycompany/servicodenomes` no ServicoNomes.

**1. Por que Health Check não garante que a requisição dará certo?**
Ele informa a saúde no instante da consulta. O servidor pode cair ou ficar ocupado entre a verificação e a tarefa, situação chamada TOCTOU. Por isso a tarefa também tem deadline e tratamento de erro.
Onde: `ClienteCore.java`, métodos `proximoServidorSaudavel` e `enviar`.

**2. Por que repetir o cálculo é aceitável aqui, mas não em PegarSenha?**
O mesmo fatorial, resumo ou hash produz o mesmo resultado. Porém, ExecutarTarefa também altera contador e histórico, então a RPC completa não é estritamente idempotente. Em PegarSenha, repetir poderia consumir duas senhas; seria necessário um ID de pedido e deduplicação para garantir o efeito uma vez.
Onde: `ClienteCore.java`, `enviar`; `CloudForgeServiceImpl.java`, `processar` e `executarTarefa`.

**3. Por que não trocar tudo por ConcurrentHashMap e AtomicLong?**
Cada estrutura protegeria suas próprias operações, mas não a regra completa de conferir vaga, ocupar e atualizar contadores juntos. Um único lock deixa essa regra mais fácil de explicar. O cliente já usa ConcurrentHashMap.newKeySet e AtomicInteger para operações independentes de diagnóstico; o nomes usa um mapa simples protegido por synchronized.
Onde: `CloudForgeServiceImpl.java`, `executarTarefa`; `NomesServiceImpl.java`, `guardar` e `copiaInstancias`; `ClienteCore.java`, `enviar`.

**4. O que acontece se o serviço de nomes cair?**
O cliente mantém a lista que já descobriu e continua verificando esses servidores. Um cliente novo não conhece endereços e não consegue descobrir as instâncias. O servidor apenas avisa se falhar o registro inicial ou o envio ao histórico global; não existe re-registro automático quando nomes volta.
Onde: `ClienteCore.java`, `atualizar`; `ServidorCloudForge.java`, `main`; `CloudForgeServiceImpl.java`, `executarTarefa`.

**5. Por que um canal por servidor e não por chamada?**
O canal gerencia conexões e pode atender várias RPCs concorrentes. Reaproveitar evita abrir transporte a cada tarefa. A revisão só mantém o canal se o endereço continuar igual e só fecha um canal retirado após o último usuário terminar.
Onde: `ClienteCore.java`, construtor de `Servidor`, `atualizar`, `reservarServidores` e `liberarServidores`.

**6. Qual a diferença entre cancelar e expirar o deadline?**
Cancelar é desistir explicitamente da chamada; deadline é um prazo máximo que vence. O cliente pode receber CANCELLED ou DEADLINE_EXCEEDED, respectivamente, e o contexto do servidor pode ficar cancelado nos dois casos. O cancelamento não desfaz um resultado já gravado.
Onde: `ClienteCore.java`, `enviar`; `CloudForgeServiceImpl.java`, `executarTarefa`, que consulta `Context.current().isCancelled()` durante o atraso.

**7. Como seria um histórico global com consistência forte?**
Precisaríamos definir um ponto único de confirmação, persistência e uma operação atômica que registrasse o pedido e o resultado, com ID de pedido para deduplicar. Poderia ser uma transação em armazenamento central; replicação tolerante a falhas exigiria coordenação adicional. Hoje o servidor conclui localmente e tenta registrar no nomes depois, então falhas podem perder registros.
Onde: `CloudForgeServiceImpl.java`, `executarTarefa`; `NomesServiceImpl.java`, `registrarProcessamento` e `obterHistoricoGlobal`.

**8. Por que volatile serve para atrasoMs e não basta para os contadores?**
Volatile torna visível a última escrita de uma configuração simples. Não transforma ler, somar e gravar em uma operação indivisível nem protege uma lista. O atraso é copiado no início da tarefa; mudanças valem para as próximas leituras. Os contadores e o histórico precisam do mesmo lock.
Onde: `CloudForgeServiceImpl.java`, `setAtrasoMs`, `setLimite`, `executarTarefa`, `estatisticas` e `copiaHistorico`.

**9. O que impede atualizar de fechar um canal entre Health e tarefa?**
O envio reserva uma cópia imutável da lista, incrementando a quantidade de usuários dos canais. A atualização pode retirar um canal da lista nova, mas o fechamento espera o último usuário liberar no finally. Duas atualizações só publicam se a lista usada como base ainda for a atual; os canais novos descartados também são fechados.
Onde: `ClienteCore.java`, `atualizar`, `enviar`, `reservarServidores`, `liberarServidores` e `fechar`.

**10. Por que a varredura do rodízio reserva apenas o início?**
Se cada tentativa consumisse o índice global novamente, outra thread poderia avançá-lo no intervalo e fazer esta consulta visitar o mesmo servidor duas vezes. Reservar o início e percorrer a cópia circularmente garante visitar cada candidato uma vez. O retry ignora o servidor da tentativa anterior.
Onde: `ClienteCore.java`, `avancarIndice`, `proximoServidorSaudavel` e `enviar`.

**11. O teste concorrencia 200 continua válido se uma instância cair?**
Os totais de respostas do cliente continuam válidos. A comparação com os contadores precisa das amostras inicial e final de cada instância; se uma faltar, retorna delta -1 e informa INCONCLUSIVO. Um reinício muito rápido com o mesmo ID pode escapar da detecção se o contador alcançar o anterior, porque o proto não tem ID de geração.
Onde: `ClienteCore.java`, `concorrencia`; `ClienteCloudForge.java`, `concorrencia`; `cloudforge.proto`, mensagem `Estatisticas`.

**12. Recebidas menos concluídas significa tarefas perdidas por corrida?**
Não necessariamente. Recebidas conta admissões; uma tarefa admitida pode ser cancelada antes de concluir. Rejeitadas conta tentativas sem vaga, e retries fazem o servidor receber mais tentativas que os pedidos originais. Em repouso, emAndamento deve voltar a zero, mesmo após deadline.
Onde: `CloudForgeServiceImpl.java`, `executarTarefa` e `estatisticas`; `ClienteCore.java`, `enviar`.

**13. O finally garante liberar a vaga até quando há erro?**
Depois de admitir a tarefa, o finally libera a vaga se a conclusão ainda não a liberou. O booleano concluiu evita decrementar duas vezes. Um processo morto à força não executa finally, mas sua memória e suas vagas desaparecem com ele; não é um vazamento persistente em outra instância.
Onde: `CloudForgeServiceImpl.java`, `executarTarefa`.

**14. Por que encerrar normalmente e matar o processo dão resultados diferentes no nomes?**
No encerramento normal, o servidor marca NOT_SERVING e tenta desregistrar com deadline. A flag atômica impede que comando sair e hook façam isso duas vezes. Matar à força pode impedir o hook, deixando endereço antigo no nomes; o Health do cliente detecta que ele não responde.
Onde: `ServidorCloudForge.java`, `encerrar`, `main` e `iniciarConsole`; `NomesServiceImpl.java`, `desregistrar`; `ClienteCore.java`, `proximoServidorSaudavel`.

**15. Como explicar entradas extremas sem prometer capacidade infinita?**
0! vale 1, 5000! usa BigInteger e devolve resumo; fatorial negativo ou maior que 5000 é rejeitado. Listas aceitam espaços e negativos, mas não campos vazios, NaN ou Infinity. Hash de texto grande depende do limite de mensagem gRPC: no teste, um milhão de caracteres funcionou e cinco milhões receberam RESOURCE_EXHAUSTED antes do método de negócio. Esse código de erro também pode significar mensagem grande, além de falta de vagas.
Onde: `CloudForgeServiceImpl.java`, `validar`, `lerNumeros`, `fatorial` e `sha256`; `ClienteCore.java`, `enviar` e `mensagemErro`.

## Roteiro de fala de 5 minutos

Preparação antes de começar: compilar os três projetos e gerar `target/cp.txt`, seguindo o README. Deixar quatro terminais PowerShell nas pastas ServicoNomes, ServidorCloudForge, ServidorCloudForge e ClienteCloudForge. Em cada terminal, configurar:

```powershell
$env:JAVA_HOME="C:\Program Files\Apache NetBeans\jdk"
$env:Path="$env:JAVA_HOME\bin;C:\Program Files\Apache NetBeans\java\maven\bin;$env:Path"
$cp="target\classes;$(Get-Content target\cp.txt -Raw)"
```

**0:00 a 0:45, descoberta e duas memórias.** Iniciar na ordem:

```powershell
# Terminal N, pasta ServicoNomes
java -cp $cp com.mycompany.servicodenomes.ServicoNomes 50150
# Terminal A, pasta ServidorCloudForge
java -cp $cp com.mycompany.servidorcloudforge.ServidorCloudForge 50151 localhost:50150
# Terminal B, pasta ServidorCloudForge
java -cp $cp com.mycompany.servidorcloudforge.ServidorCloudForge 50152 localhost:50150
# Terminal C, pasta ClienteCloudForge
java -cp $cp com.mycompany.clientecloudforge.ClienteCloudForge localhost:50150
```

Fala: "O nomes informa onde estão as instâncias. Elas se registraram ao iniciar, e o cliente recebeu a lista pela rede. Cada processo tem seus próprios contadores. As portas dos servidores vieram do nomes."

**0:45 a 1:25, rodízio e estado local.** No cliente:

```text
rr 6
stats
```

Fala: "As seis tarefas alternam entre as instâncias. As duas começaram com tarefa número 1, porque o ID é local. O cliente consulta Health antes de enviar e reaproveita os canais. Só o ponto de partida do rodízio precisa do lock; a consulta de rede fica fora dele."

**1:25 a 2:05, saúde e recuperação.** No B, `notserving`; no cliente, `rr 4`; no B, `serving`; no cliente, `rr 2`.

Fala: "B continua ligado, mas não recebe tarefas do cliente enquanto anuncia NOT_SERVING. Quando volta a SERVING, participa de novo. Saúde é uma fotografia do instante, então ainda precisamos tratar falhas na tarefa."

**2:05 a 2:45, deadline.** No A, `atraso 7000`; no cliente, `rr 2`; depois, no A, `atraso 0`; no cliente, `stats`.

Fala: "O atraso de sete segundos supera o deadline de cinco. O cliente trata DEADLINE_EXCEEDED e tenta a outra instância. O servidor percebe o cancelamento durante a espera e libera a vaga no finally. A consulta stats deve mostrar em andamento voltando a zero."

**2:45 a 3:50, concorrência e limite.** No cliente, `concorrencia 200`. Depois, em A e B, `limite 2` e `atraso 100`; no cliente, `concorrencia 200` e `stats`; em A e B, `atraso 0` e `limite 4`.

Fala: "São 200 pedidos executados por até 32 threads. Com as instâncias estáveis e sem outro cliente, a soma do aumento das concluídas deve conferir com as respostas, sem IDs repetidos por instância. Com duas vagas e atraso, alguns pedidos são recusados. Rejeição não é perda do contador e não significa que há fila. Se uma instância cair durante o teste, a comparação dos contadores fica inconclusiva."

**3:50 a 4:35, queda com cliente aberto.** Em um quinto PowerShell, encerrar à força somente B:

```powershell
Stop-Process -Id (Get-NetTCPConnection -State Listen -LocalPort 50152).OwningProcess
```

No cliente, `rr 4`. Fala: "B caiu sem desregistrar. O endereço pode continuar no nomes, mas o Health falha e o cliente usa A. Não precisei reiniciar o cliente."

**4:35 a 5:00, nenhum saudável e fechamento.** No A, `notserving`; no cliente, `enviar HASH teste`. Depois, no A, `serving`; no cliente, `enviar HASH recuperou`, seguido de `sair`; no A, `sair`; encerrar N com Ctrl+C.

Fala: "Sem nenhum servidor saudável, o cliente informa o problema e permanece aberto. Quando A volta, as chamadas funcionam. Synchronized protege as threads de cada processo; um histórico global confiável exigiria coordenação e armazenamento compartilhados. O bônus atual centraliza registros, mas não promete transação nem execução única."
