
defmodule ProcessSup do
  use Supervisor

  def start_link({name, spec}) do
    #Supervisor.start_link(spec, [name: name, strategy: elem(strat, 0), max_restarts: elem(strat, 1), max_seconds: elem(strat, 2)])
    #Supervisor.start_link(__MODULE__, spec, name: name)
    :supervisor.start_link({:local, name}, __MODULE__, spec)
  end
  def init(spec) do
    {:ok, spec}
  end
end
