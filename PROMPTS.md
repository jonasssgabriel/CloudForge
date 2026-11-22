# PROMPTS.md: CloudForge (processamento distribuído com gRPC)

Jonas e grupo, SPD 2026-2. Trabalho: serviço distribuído com gRPC (client-side load balancing, Health Check, região crítica).

Ferramenta usada: Claude Code, com outros agentes orquestrados via Orca (subagentes Sonnet, Codex, Antigravity).

**Como trabalhamos:** cada resposta das IAs foi lida pelo grupo antes de entrar no projeto. O enunciado da professora manda mais que qualquer prompt. Pedimos simplicidade em tudo, porque a prova individual de 11/12 é sobre o código. Nada foi considerado pronto sem ver o teste rodando de verdade.

## Prompt 1: implementar o CloudForge do zero

> Você vai implementar, do zero, o trabalho "Serviço distribuído com gRPC" da disciplina SPD do Jonas e do grupo. Tema: CloudForge, sistema de processamento distribuído em nuvem. Siga o enunciado oficial (ele manda mais que este prompt). O código precisa ser simples, didático e explicável por um estudante, com comentários curtos em português. Três projetos Maven (nomes, servidor, cliente), com cópia dos `.proto` em cada um. Fases: 1) mínimo de 27/11; 2) completo de 04/12 com round-robin e Health Check manuais; 3) bônus do histórico global. Execute de verdade os testes da seção 6 do enunciado e registre a saída real. Sem commit e sem push.

**O que foi pedido (resumo fiel):**
- Serviço de nomes na porta 50050 (Registrar, Desregistrar, Listar), com acesso sincronizado.
- Servidor `srv-<porta>` com `grpc.health.v1.Health`, três tarefas (fatorial, processamento de dados, SHA-256), estado próprio por instância (contador de tarefa, contadores, histórico dos últimos 1000, limite de simultâneas) e console para `serving`, `notserving`, `atraso`, `limite`, `stats`, `historico`.
- Regras de sincronização: mesmo lock em todo acesso ao estado, nunca segurar o lock durante processamento, `sleep` ou rede.
- Cliente com round-robin manual, canal criado uma vez, só o avanço do índice `synchronized`, Health Check fora do lock, deadlines de 2 s (health) e 5 s (tarefa), retry de 1 vez, mensagem quando nenhum servidor está saudável e comando `concorrencia`.
- Entregáveis: scripts `.bat`, README completo, `docs/DECISOES.md`, `docs/EXPLICACAO-PARA-PROVA.md` e `docs/EVIDENCIAS.md`.
- Bônus: histórico global no serviço de nomes, ligado por `--global`.

**O que a IA fez:** montou os 3 projetos (`ServicoNomes`, `ServidorCloudForge`, `ClienteCloudForge`), os `.proto`, `CloudForgeServiceImpl`, `ClienteCore`, os scripts, o `CorridaDemo` e a documentação, e rodou os testes T1 a T9 com processos reais.

**O que o grupo aceitou/mudou:** aceitamos a estrutura toda. Registramos em `docs/DECISOES.md` as escolhas simples (fatorial no lugar de Fibonacci, `synchronized` no nomes, instância caída permanece na lista, limite por contador sob lock, bônus por `--global`, retry de 1 vez).

## Prompt 2: "use todas as IAs"

> Use todas as IAs que a gente tem.

**O que foi pedido:** dividir o trabalho entre ferramentas diferentes, em vez de uma IA só fazer tudo.

**O que foi feito:**
- **Claude Code** como orquestrador, com **subagentes Sonnet** para a documentação, a revisão do código e a conferência com o PDF.
- **Codex** escreveu o cliente (`ClienteCore` e o console `ClienteCloudForge`).
- **Antigravity** fez a interface gráfica (GUI Swing).
- Foram tentados também **OpenCode** e **GitHub Copilot**, mas **falharam por problema de ambiente** (não conseguiram rodar aqui). O trabalho deles ficou com as outras ferramentas.

**O que o grupo aceitou/mudou:** cada resposta de cada IA foi revisada pelo grupo e por outro agente antes de entrar. Quando o código de uma ferramenta não batia com o enunciado, foi corrigido no pedido seguinte.

## Prompt 3: interface bonita, sem cara de IA

> A interface precisa ficar muito bonita. Tema claro, sem cara de IA (sem card genérico, sem gradiente sem função). Use como referência os projetos anteriores do grupo e o que existe de bom no Behance, na Vercel e no Linear. Quero animações.

**O que foi pedido:** uma GUI do cliente em tema claro, com aparência cuidada e animações que mostrem o que o sistema está fazendo.

**O que foi feito:** a GUI Swing (`ClienteGUI`, `TelaCloudForge`, `PainelTopologia`, `PainelInstancias`, `PainelRodizio`, `PainelLog`, `TemaCloudForge`) com tema claro próprio, topologia animada (nomes, instâncias e cliente), cartões das instâncias com a saúde e um botão que executa um roteiro de demonstração automático (`rr 6` e depois concorrência 50). Ela usa o mesmo `ClienteCore` do console, então o round-robin e o Health Check são os mesmos.

**O que o grupo aceitou/mudou:** o console continua disponível e é o que usamos para o roteiro dos 9 testes. A GUI é um complemento para mostrar o rodízio e a saúde ao vivo.

## Prompt 4: conferir a conformidade com o PDF

> Confere o que foi feito contra o PDF da professora (Aula13, trabalho gRPC client-side load balancing). Quero a tabela de conformidade no README, item por item, com arquivo e método.

**O que foi pedido:** ler o enunciado (seções 2, 3, 4, 6 e 7) e conferir cada item com o código real.

**O que a IA fez:** leu os `.java` reais, montou no `README.md` a tabela de conformidade com arquivo e método para cada item, a lista dos 9 testes do roteiro, e atualizou `docs/DECISOES.md` e `docs/EXPLICACAO-PARA-PROVA.md` com os mesmos apontamentos. As saídas completas dos testes ficam em `docs/EVIDENCIAS.md`, gerado à parte.

**O que o grupo aceitou/mudou:** conferimos por amostragem os métodos citados abrindo cada arquivo. Os resultados dos testes não foram copiados para o README, só resumidos.

## Referências oficiais

- Enunciado: Aula13, Trabalho gRPC client-side load balancing (PDF da professora).
- Health Checking: https://grpc.io/docs/guides/health-checking/
- gRPC Load Balancing: https://grpc.io/docs/guides/load-balancing/
- Deadlines: https://grpc.io/docs/guides/deadlines/
- Cancelamento: https://grpc.io/docs/guides/cancellation/
- Códigos de status: https://grpc.io/docs/guides/status-codes/
